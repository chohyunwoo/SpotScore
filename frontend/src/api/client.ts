const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * HTTP 상태 코드를 함께 들고 다니는 에러.
 * 404(아직 배치가 안 돌아 SCORE_CACHE/등 row가 없는 경우)와 그 외 실패(5xx, 네트워크 오류)를
 * 화면에서 구분해 보여주기 위해 사용 — CLAUDE.md "N/A(비공개) 값 처리"와는 별개로,
 * "데이터가 아직 없음"과 "요청 자체가 실패함"을 사용자에게 다르게 안내하기 위한 용도.
 * 401(로그인 필요)/409(중복) 같은 인증·즐겨찾기 흐름 분기에도 status를 그대로 쓴다.
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * 세션 로그인 도입으로 모든 요청에 쿠키(JSESSIONID/XSRF-TOKEN)를 실어보내야 하므로
 * credentials: 'include'를 기본값으로 둔다(교차 오리진에서도 쿠키 전송). 백엔드
 * SecurityConfig의 CORS가 allowCredentials(true)로 이를 허용한다.
 */
function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

async function parseBody<T>(response: Response, url: string): Promise<T> {
  // 배치 미실행/204 No Content 등으로 바디가 비어있을 수 있음 — response.json()이
  // 빈 문자열에 대해 던지는 파싱 예외를 막기 위해 텍스트로 먼저 확인.
  const text = await response.text();

  if (!response.ok) {
    // 백엔드 GlobalExceptionHandler/보안 계층은 실패 시 ErrorResponse(JSON)를 내려준다.
    // 그 message를 꺼내 사용자에게 그대로 보여주고(로그인 실패/중복 이메일 등), 없으면
    // 상태 코드 기반 기본 메시지로 대체한다.
    let message = `API 요청 실패: ${response.status} ${response.statusText} (${url})`;
    if (text.length > 0) {
      try {
        const parsed = JSON.parse(text) as { message?: string };
        if (parsed.message) {
          message = parsed.message;
        }
      } catch {
        // JSON이 아니면 기본 메시지 유지.
      }
    }
    throw new ApiError(response.status, message);
  }

  if (text.length === 0) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}

export async function fetchJson<T>(path: string): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const response = await fetch(url, { credentials: 'include' });
  return parseBody<T>(response, url);
}

/**
 * 상태를 바꾸는 요청(POST/PUT/DELETE)용 공통 헬퍼. 세션 쿠키와 함께, 백엔드가
 * XSRF-TOKEN 쿠키로 내려준 CSRF 토큰을 X-XSRF-TOKEN 헤더로 되돌려보낸다
 * (SecurityConfig의 CookieCsrfTokenRepository 규약). 공개 POST(로그인/회원가입/챗봇)는
 * 서버에서 CSRF 예외 처리되지만, 헤더를 함께 보내도 무해하므로 일괄 처리한다.
 */
async function mutate<T>(method: 'POST' | 'PUT' | 'DELETE', path: string, body?: unknown): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const headers: Record<string, string> = {};
  const csrfToken = readCookie('XSRF-TOKEN');
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
  const init: RequestInit = { method, credentials: 'include', headers };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const response = await fetch(url, init);
  return parseBody<T>(response, url);
}

/** POST 요청 헬퍼 — fetchJson과 동일한 에러/빈 바디 처리 규칙을 따른다. */
export async function postJson<T>(path: string, body: unknown): Promise<T> {
  return mutate<T>('POST', path, body);
}

export async function deleteJson<T>(path: string): Promise<T> {
  return mutate<T>('DELETE', path);
}
