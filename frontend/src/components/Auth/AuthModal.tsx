import { useState } from 'react';
import type { FormEvent } from 'react';
import styled from 'styled-components';
import { ApiError } from '../../api/client';
import { useAuth } from '../../context/AuthContext';
import type { AuthModalMode } from '../../context/AuthContext';

const Backdrop = styled.div`
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.lg};
  background: rgba(16, 24, 40, 0.45);
`;

const Card = styled.div`
  width: 100%;
  max-width: 380px;
  background: ${({ theme }) => theme.colors.surface};
  border-radius: ${({ theme }) => theme.radius.lg};
  box-shadow: ${({ theme }) => theme.shadow.panel};
  padding: ${({ theme }) => theme.spacing.xl};
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.md};
`;

const Title = styled.h2`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.h2.size};
  font-weight: ${({ theme }) => theme.typography.h2.weight};
  color: ${({ theme }) => theme.colors.textPrimary};
`;

const Description = styled.p`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  line-height: ${({ theme }) => theme.typography.body.lineHeight};
`;

const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const Field = styled.label`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
  font-size: ${({ theme }) => theme.typography.label.size};
  font-weight: ${({ theme }) => theme.typography.label.weight};
  color: ${({ theme }) => theme.colors.textSecondary};
`;

const Input = styled.input`
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  border: 1px solid ${({ theme }) => theme.colors.border};
  border-radius: ${({ theme }) => theme.radius.sm};
  font-family: inherit;
  font-size: ${({ theme }) => theme.typography.body.size};
  color: ${({ theme }) => theme.colors.textPrimary};

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.accent};
  }
`;

const SubmitButton = styled.button`
  margin-top: ${({ theme }) => theme.spacing.xs};
  padding: ${({ theme }) => theme.spacing.sm};
  border: none;
  border-radius: ${({ theme }) => theme.radius.sm};
  background: ${({ theme }) => theme.colors.accent};
  color: ${({ theme }) => theme.colors.onAccent};
  font-family: inherit;
  font-size: ${({ theme }) => theme.typography.body.size};
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  cursor: pointer;

  &:hover {
    background: ${({ theme }) => theme.colors.accentHover};
  }

  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
`;

const ErrorText = styled.p`
  margin: 0;
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.danger};
`;

const SwitchRow = styled.div`
  font-size: ${({ theme }) => theme.typography.bodySmall.size};
  color: ${({ theme }) => theme.colors.textSecondary};
  text-align: center;
`;

const SwitchButton = styled.button`
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-family: inherit;
  font-size: inherit;
  font-weight: ${({ theme }) => theme.typography.weight.semibold};
  color: ${({ theme }) => theme.colors.accent};
`;

const CloseButton = styled.button`
  align-self: flex-end;
  margin-top: -${({ theme }) => theme.spacing.sm};
  border: none;
  background: none;
  cursor: pointer;
  font-size: 18px;
  line-height: 1;
  color: ${({ theme }) => theme.colors.textTertiary};
`;

const COPY: Record<AuthModalMode, { title: string; description: string; submit: string; switchTo: AuthModalMode; switchPrompt: string; switchAction: string }> = {
  login: {
    title: '로그인',
    description: '관심 지역을 저장하고 여러 후보지를 나란히 비교하려면 로그인하세요.',
    submit: '로그인',
    switchTo: 'register',
    switchPrompt: '아직 계정이 없으신가요?',
    switchAction: '회원가입',
  },
  register: {
    title: '회원가입',
    description: '이메일과 비밀번호(8자 이상)로 가입하면 즐겨찾기와 비교 기능을 쓸 수 있어요.',
    submit: '회원가입',
    switchTo: 'login',
    switchPrompt: '이미 계정이 있으신가요?',
    switchAction: '로그인',
  },
};

/**
 * 로그인/회원가입 모달. 표시 여부·모드는 AuthContext(authModalMode)가 제어한다.
 * 성공하면 AuthContext가 모달을 닫는다(applyAuthenticatedUser).
 */
export function AuthModal() {
  const { authModalMode, openAuthModal, closeAuthModal, login, register } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (authModalMode === null) {
    return null;
  }

  const copy = COPY[authModalMode];

  const resetFields = () => {
    setError(null);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      if (authModalMode === 'register') {
        await register({ email, password, displayName });
      } else {
        await login({ email, password });
      }
      // 성공 시 AuthContext가 모달을 닫으므로 여기서 별도 처리 불필요.
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '요청 처리 중 오류가 발생했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Backdrop onClick={closeAuthModal}>
      <Card onClick={(event) => event.stopPropagation()}>
        <CloseButton type="button" onClick={closeAuthModal} aria-label="닫기">
          ×
        </CloseButton>
        <Title>{copy.title}</Title>
        <Description>{copy.description}</Description>
        <Form onSubmit={handleSubmit}>
          {authModalMode === 'register' && (
            <Field>
              표시 이름
              <Input
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="예: 창업준비생"
                maxLength={50}
                required
              />
            </Field>
          )}
          <Field>
            이메일
            <Input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              autoComplete="email"
              required
            />
          </Field>
          <Field>
            비밀번호
            <Input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={authModalMode === 'register' ? '8자 이상' : ''}
              autoComplete={authModalMode === 'register' ? 'new-password' : 'current-password'}
              minLength={authModalMode === 'register' ? 8 : undefined}
              required
            />
          </Field>
          {error && <ErrorText>{error}</ErrorText>}
          <SubmitButton type="submit" disabled={submitting}>
            {submitting ? '처리 중...' : copy.submit}
          </SubmitButton>
        </Form>
        <SwitchRow>
          {copy.switchPrompt}{' '}
          <SwitchButton
            type="button"
            onClick={() => {
              resetFields();
              openAuthModal(copy.switchTo);
            }}
          >
            {copy.switchAction}
          </SwitchButton>
        </SwitchRow>
      </Card>
    </Backdrop>
  );
}
