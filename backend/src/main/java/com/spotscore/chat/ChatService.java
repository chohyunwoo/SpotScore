package com.spotscore.chat;

import com.spotscore.chat.dto.ChatRequest;
import com.spotscore.chat.dto.ChatResponse;
import com.spotscore.chat.dto.GroqChatResponse;
import com.spotscore.chat.dto.GroqMessage;
import com.spotscore.chat.dto.GroqTool;
import com.spotscore.chat.dto.GroqToolCall;
import com.spotscore.chat.tool.ToolRegistry;
import com.spotscore.config.GroqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Groq(OpenAI 호환) tool-calling 루프를 실행한다. 이 프로젝트의 "실시간 외부 API
 * 호출 금지" 원칙은 SGIS/상권정보 기반 점수 계산 파이프라인(배치 전용)에 대한
 * 것이고, 챗봇은 애초에 요청 시점 인터랙티브 호출이 의도된 별도 기능이다 - Groq를
 * 매 요청마다 호출하는 것은 규칙 위반이 아니다.
 *
 * 대화 상태는 서버에 저장하지 않는다(무로그인 프로젝트라 세션을 식별할 근거가
 * 없고, 프론트가 매 요청마다 전체 이력을 다시 보낸다 - ChatWidget의 로컬 state가
 * 유일한 저장소).
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            당신은 SpotScore 창업 입지 추천 대시보드의 어시스턴트입니다.
            모든 수치(점수, 순위, 인구, 업소 수 등)는 반드시 제공된 tool 호출 결과에서만
            가져와 답하십시오. tool을 호출하지 않고 숫자를 추정하거나 지어내지 마십시오 -
            이는 이 프로젝트의 핵심 원칙입니다.
            tool 결과에 데이터가 없으면("found": false, 빈 목록, 또는 message 필드로
            안내된 경우) 없다고 답하고 임의로 채우지 마십시오.
            연령적합도 점수(ageScore)를 언급할 때는 tool 결과의 ageScoreCaveat 문구를
            반드시 함께 전달하십시오.

            사용자가 "왜 이 점수인지", "이유가 뭔지" 등 점수의 근거를 물으면, 막연하게
            "인구가 많아서", "경쟁이 적어서"라고만 답하지 말고 반드시 get_score_weights를
            호출해 실제 가중치를 가져온 뒤, get_region_detail의 populationScore/
            householdScore/densityScore(해당하면 ageScore)에 그 가중치를 곱한 값을
            각각 보여주며 totalScore가 어떻게 합산되는지 계산 과정으로 설명하십시오.
            어느 항목이 가중치×점수 기여도가 가장 큰지도 짚어주십시오.

            숫자뿐 아니라 정성적 주장("다른 업종에서도 상위권에 지속적으로 등장한다",
            "전반적으로 인기가 많다" 등)도 실제로 tool을 호출해 확인한 결과가 아니면
            절대 쓰지 마십시오. 예를 들어 "전체 업종 중 가장 매력적인 동네"처럼 특정
            업종을 지정하지 않은 질문을 받으면, 반드시 get_featured_industries로 업종
            목록을 확인한 뒤 그 중 실제로 get_ranking_by_industry를 호출해 조회한
            업종에 대해서만 근거를 들어 답하십시오. 답변에는 실제로 어떤 업종을
            확인했는지 명시하고, 확인하지 않은 업종에 대해서는 "확인하지 않았다"고
            분명히 밝히거나 아예 언급하지 마십시오 - "다른 업종에서도 그럴 것"이라는
            식의 추측은 이 프로젝트가 절대 허용하지 않는 임의 추정입니다.
            """;

    private final GroqClient groqClient;
    private final ToolRegistry toolRegistry;
    private final GroqProperties properties;

    public ChatService(GroqClient groqClient, ToolRegistry toolRegistry, GroqProperties properties) {
        this.groqClient = groqClient;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
    }

    public ChatResponse handle(ChatRequest request) {
        if (request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("messages는 최소 1개 이상이어야 합니다");
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("GROQ_API_KEY 미설정 - 챗봇 요청을 처리하지 않고 안내 메시지 반환");
            return new ChatResponse("GROQ_API_KEY가 설정되지 않아 챗봇을 사용할 수 없습니다.");
        }

        List<GroqMessage> conversation = buildInitialMessages(request);
        List<GroqTool> tools = toolRegistry.toGroqToolDefinitions();

        for (int iteration = 0; iteration < properties.maxToolIterations(); iteration++) {
            GroqChatResponse response = groqClient.chatCompletion(conversation, tools).block();
            GroqMessage message = response.choices().get(0).message();

            if (message.toolCalls() == null || message.toolCalls().isEmpty()) {
                // gpt-oss 계열은 reasoning(생각 과정) 토큰을 content와 별도로 소비한다 -
                // max_tokens가 부족하면 tool_calls 없이 content가 빈 문자열/null로만
                // 오는 경우가 실제로 관측됨(2026-08). 빈 답변을 그대로 보여주지 않고
                // 사과 메시지로 대체한다.
                if (hasText(message.content())) {
                    return new ChatResponse(message.content());
                }
                log.warn("모델이 tool_calls 없이 빈 content를 반환함 - max_tokens 부족 가능성");
                break;
            }

            conversation.add(message);
            for (GroqToolCall call : message.toolCalls()) {
                String result = toolRegistry.execute(call.function().name(), call.function().arguments());
                conversation.add(GroqMessage.toolResult(call.id(), result));
            }
        }

        // 위 루프가 (a) maxToolIterations만큼 tool을 다 썼는데도 모델이 계속 tool_calls만
        // 반환하거나(후보 지역 여러 곳을 비교하려다 한도 초과, 2026-08 확인), (b) 빈
        // content로 조기 종료된 경우 여기로 온다. 두 경우 모두 이미 조회해둔 tool
        // 결과를 버리지 않기 위해, tools를 빈 목록으로 넘겨 더 이상 tool을 호출할 수
        // 없게 강제하면서 마지막으로 한 번 더 호출해 답을 종합하게 한다.
        log.warn("최종 답변을 강제 생성 - 지금까지 모은 정보만으로 종합 시도");
        conversation.add(GroqMessage.system("더 이상 tool을 호출할 수 없습니다. 지금까지 조회한 정보만으로 지금 바로 최종 답변을 작성하십시오."));
        GroqChatResponse finalResponse = groqClient.chatCompletion(conversation, List.of()).block();
        String finalContent = finalResponse.choices().get(0).message().content();
        if (hasText(finalContent)) {
            return new ChatResponse(finalContent);
        }

        log.warn("최종 답변 강제 생성도 실패 - content가 비어있음");
        return new ChatResponse("죄송해요, 답변을 만드는 데 어려움이 있었어요. 질문을 조금 더 구체적으로 다시 말씀해 주시겠어요?");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<GroqMessage> buildInitialMessages(ChatRequest request) {
        List<GroqMessage> messages = new ArrayList<>();
        messages.add(GroqMessage.system(SYSTEM_PROMPT));

        if (request.industryCode() != null || request.regionCode() != null) {
            String hint = "현재 사용자가 대시보드에서 선택 중인 참고 정보 - industryCode: "
                    + request.industryCode() + ", regionCode: " + request.regionCode()
                    + " (참고용 힌트일 뿐입니다. 사용자가 다른 지역/업종을 물으면 그것을 따르십시오.)";
            messages.add(GroqMessage.system(hint));
        }

        request.messages().forEach(chatMessage -> {
            if ("user".equals(chatMessage.role())) {
                messages.add(GroqMessage.user(chatMessage.content()));
            } else {
                messages.add(new GroqMessage("assistant", chatMessage.content(), null, null));
            }
        });
        return messages;
    }
}
