package com.spotscore.chat.tool;

import com.spotscore.chat.dto.GroqFunctionDefinition;
import com.spotscore.chat.dto.GroqTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 모든 ChatTool 빈을 자동 수집해 Groq(OpenAI 호환) tool 스키마 목록을 만들고, 이름으로
 * 실행을 라우팅한다. execute()는 어떤 예외가 나든 절대 던지지 않고 에러 형태의 tool
 * 결과 문자열로 변환한다 - 개별 tool 하나가 실패해도 /api/v1/chat 요청 전체가 502로
 * 죽지 않게 하는 최종 방어선이다.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ChatTool> toolsByName;
    private final List<GroqTool> toolDefinitions;

    public ToolRegistry(List<ChatTool> tools) {
        this.toolsByName = tools.stream().collect(Collectors.toMap(ChatTool::name, Function.identity()));
        this.toolDefinitions = tools.stream()
                .map(tool -> GroqTool.function(
                        new GroqFunctionDefinition(tool.name(), tool.description(), tool.parametersSchema())))
                .toList();
        log.info("챗봇 tool 등록 완료 - 등록된 tool: {}", toolsByName.keySet());
    }

    public List<GroqTool> toGroqToolDefinitions() {
        return toolDefinitions;
    }

    public String execute(String name, String argumentsJson) {
        ChatTool tool = toolsByName.get(name);
        if (tool == null) {
            log.warn("알 수 없는 tool 호출 요청 - name: {}", name);
            return "{\"error\":\"알 수 없는 tool입니다: " + name + "\"}";
        }
        try {
            log.debug("tool 실행 시작 - name: {}, arguments: {}", name, argumentsJson);
            return tool.execute(argumentsJson);
        } catch (Exception ex) {
            log.error("tool 실행 중 예외 발생 - name: {}", name, ex);
            return "{\"error\":\"tool 실행 중 오류가 발생했습니다\"}";
        }
    }
}
