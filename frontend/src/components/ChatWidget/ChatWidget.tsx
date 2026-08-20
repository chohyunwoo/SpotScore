import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import styled from 'styled-components';
import { useSendChatMessage } from '../../api/chat';
import type { ChatMessage } from '../../types/domain';

const Wrapper = styled.div`
  position: absolute;
  bottom: ${({ theme }) => theme.spacing.md};
  left: ${({ theme }) => theme.spacing.md};
  /* SlidePanel(상세 패널, z-index 20)보다 위에 떠야 한다 - 챗봇을 여는 건 사용자의
     명시적 행동이라 상세 패널이 열려있어도 그 위에 보여야 자연스럽다. */
  z-index: 25;
  display: flex;
  flex-direction: column-reverse;
  align-items: flex-start;
  gap: ${({ theme }) => theme.spacing.sm};

  @media (max-width: ${({ theme }) => theme.breakpoint.tablet}) {
    left: ${({ theme }) => theme.spacing.sm};
    bottom: ${({ theme }) => theme.spacing.sm};
  }
`;

const FabButton = styled.button`
  width: 48px;
  height: 48px;
  border-radius: ${({ theme }) => theme.radius.pill};
  border: none;
  background: ${({ theme }) => theme.colors.accent};
  color: ${({ theme }) => theme.colors.onAccent};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  cursor: pointer;
  font-size: ${({ theme }) => theme.typography.h3.size};
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: ${({ theme }) => theme.colors.accentHover};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.accent};
    outline-offset: 2px;
  }
`;

/* 항상 마운트해 두고 display로만 여닫는다 - RegionSearchBox와 달리 대화 이력을
   로컬 state로 들고 있어야 해서, 언마운트하면 닫을 때마다 대화가 사라져 버린다.
   답변에 지역 비교 표가 자주 나오는데 기본 크기로는 좁아서, 별도 JS 드래그
   로직 없이 브라우저 기본 resize 핸들(우측 하단)로 사용자가 직접 늘릴 수 있게
   한다 - resize는 overflow가 visible이 아니어야 동작한다(hidden으로 이미 충족). */
const Panel = styled.div<{ $open: boolean }>`
  display: ${({ $open }) => ($open ? 'flex' : 'none')};
  flex-direction: column;
  width: 400px;
  height: 480px;
  min-width: 320px;
  min-height: 360px;
  max-width: min(90vw, 900px);
  max-height: min(85vh, 800px);
  resize: both;
  border-radius: ${({ theme }) => theme.radius.md};
  border: 1px solid ${({ theme }) => theme.colors.border};
  background: ${({ theme }) => theme.colors.surface};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  overflow: hidden;
`;

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.sm} ${({ theme }) => theme.spacing.md};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border};
  font-size: ${({ theme }) => theme.typography.h3.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const CloseButton = styled.button`
  border: none;
  background: transparent;
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.h3.size};
  line-height: 1;
  cursor: pointer;
  padding: ${({ theme }) => theme.spacing.xs};
`;

const MessageList = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: ${({ theme }) => theme.spacing.md};
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const EmptyHint = styled.p`
  color: ${({ theme }) => theme.colors.textSecondary};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  text-align: center;
  margin: auto 0;
`;

const MessageBubble = styled.div<{ $role: 'user' | 'assistant' }>`
  align-self: ${({ $role }) => ($role === 'user' ? 'flex-end' : 'flex-start')};
  max-width: 85%;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  line-height: ${({ theme }) => theme.typography.bodySmall.lineHeight};
  white-space: pre-wrap;
  background: ${({ $role, theme }) => ($role === 'user' ? theme.colors.accent : theme.colors.backgroundAlt)};
  color: ${({ $role, theme }) => ($role === 'user' ? theme.colors.onAccent : theme.colors.textPrimary)};
`;

const ErrorBubble = styled(MessageBubble)`
  background: ${({ theme }) => theme.colors.danger};
  color: ${({ theme }) => theme.colors.onAccent};
`;

/**
 * 챗봇 답변이 마크다운(표, 굵게, 목록 등)으로 오는 경우가 많아 ReactMarkdown으로
 * 렌더링한다 - 부모 MessageBubble의 white-space: pre-wrap은 렌더링된 HTML에는
 * 불필요해서 여기서 normal로 되돌린다. 표는 열이 많아 패널 폭을 넘길 수 있어
 * 테이블 자체에 가로 스크롤을 준다(패널 전체가 옆으로 밀리지 않도록).
 */
const MarkdownContent = styled.div`
  white-space: normal;

  & > *:first-child {
    margin-top: 0;
  }
  & > *:last-child {
    margin-bottom: 0;
  }

  & p {
    margin: 0 0 ${({ theme }) => theme.spacing.xs} 0;
  }

  & strong {
    font-weight: ${({ theme }) => theme.typography.weight.semibold};
  }

  & ul,
  & ol {
    margin: ${({ theme }) => theme.spacing.xs} 0;
    padding-left: ${({ theme }) => theme.spacing.lg};
  }

  & code {
    background: rgba(0, 0, 0, 0.06);
    padding: 1px 4px;
    border-radius: 4px;
    font-size: 0.9em;
  }

  & table {
    display: block;
    overflow-x: auto;
    border-collapse: collapse;
    width: max-content;
    max-width: 100%;
    margin: ${({ theme }) => theme.spacing.xs} 0;
  }

  & th,
  & td {
    border: 1px solid ${({ theme }) => theme.colors.border};
    padding: 4px 8px;
    text-align: left;
    font-size: ${({ theme }) => theme.typography.caption.size};
  }

  & th {
    background: rgba(0, 0, 0, 0.04);
  }

  & a {
    color: ${({ theme }) => theme.colors.accent};
  }
`;

const InputRow = styled.form`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => theme.spacing.sm};
  border-top: 1px solid ${({ theme }) => theme.colors.border};
`;

const ChatInput = styled.input`
  flex: 1;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border-radius: ${({ theme }) => theme.radius.pill};
  border: 1px solid ${({ theme }) => theme.colors.border};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-family: inherit;
  color: ${({ theme }) => theme.colors.textPrimary};

  &:disabled {
    color: ${({ theme }) => theme.colors.textTertiary};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.accent};
    outline-offset: 1px;
  }
`;

const SendButton = styled.button`
  border: none;
  border-radius: ${({ theme }) => theme.radius.pill};
  padding: 0 ${({ theme }) => theme.spacing.md};
  background: ${({ theme }) => theme.colors.accent};
  color: ${({ theme }) => theme.colors.onAccent};
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  cursor: pointer;

  &:disabled {
    background: ${({ theme }) => theme.colors.textTertiary};
    cursor: not-allowed;
  }
`;

interface ChatWidgetProps {
  industryCode: string | null;
  regionCode: string | null;
}

/**
 * 좌하단 플로팅 창업 상담 챗봇. RegionSearchBox와 같은 "지도 위 플로팅 위젯" 패턴을
 * 따르되, industryCode/regionCode는 useSelection()을 직접 호출하지 않고 부모
 * (MapDashboard)로부터 props로 받는다 - RegionSearchBox와 동일한 프롭 드릴링 방식.
 *
 * 서버(ChatService)는 대화 상태를 저장하지 않으므로, 이 컴포넌트의 messages state가
 * 유일한 대화 이력 저장소다 - 패널을 닫아도 언마운트되지 않도록 display로만 여닫는다.
 */
export function ChatWidget({ industryCode, regionCode }: ChatWidgetProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const { mutate, isPending, isError } = useSendChatMessage();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = inputValue.trim();
    if (!trimmed || isPending) return;

    const nextMessages: ChatMessage[] = [...messages, { role: 'user', content: trimmed }];
    setMessages(nextMessages);
    setInputValue('');

    mutate(
      { messages: nextMessages, industryCode, regionCode },
      {
        onSuccess: (response) => {
          setMessages((prev) => [...prev, { role: 'assistant', content: response.reply }]);
        },
      },
    );
  };

  return (
    <Wrapper>
      <FabButton type="button" onClick={() => setIsOpen((open) => !open)} aria-label="창업 상담 챗봇 열기/닫기">
        {isOpen ? '×' : '💬'}
      </FabButton>
      <Panel $open={isOpen}>
        <PanelHeader>
          창업 상담 챗봇
          <CloseButton type="button" onClick={() => setIsOpen(false)} aria-label="닫기">
            ×
          </CloseButton>
        </PanelHeader>
        <MessageList>
          {messages.length === 0 && (
            <EmptyHint>
              궁금한 지역이나 업종을 물어보세요.
              <br />
              예: "강남구에서 카페 창업하기 좋은 동네 알려줘"
            </EmptyHint>
          )}
          {messages.map((message, index) => (
            <MessageBubble key={index} $role={message.role}>
              {message.role === 'assistant' ? (
                <MarkdownContent>
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
                </MarkdownContent>
              ) : (
                message.content
              )}
            </MessageBubble>
          ))}
          {isPending && <MessageBubble $role="assistant">생각 중...</MessageBubble>}
          {isError && !isPending && (
            <ErrorBubble $role="assistant">답변을 가져오지 못했습니다. 잠시 후 다시 시도해주세요.</ErrorBubble>
          )}
        </MessageList>
        <InputRow onSubmit={handleSubmit}>
          <ChatInput
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder="메시지를 입력하세요"
            disabled={isPending}
            aria-label="챗봇에게 메시지 보내기"
          />
          <SendButton type="submit" disabled={isPending || inputValue.trim().length === 0}>
            보내기
          </SendButton>
        </InputRow>
      </Panel>
    </Wrapper>
  );
}
