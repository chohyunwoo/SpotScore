const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/**
 * HTTP 상태 코드를 함께 들고 다니는 에러.
 * 404(아직 배치가 안 돌아 SCORE_CACHE/등 row가 없는 경우)와 그 외 실패(5xx, 네트워크 오류)를
 * 화면에서 구분해 보여주기 위해 사용 — CLAUDE.md "N/A(비공개) 값 처리"와는 별개로,
 * "데이터가 아직 없음"과 "요청 자체가 실패함"을 사용자에게 다르게 안내하기 위한 용도.
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function fetchJson<T>(path: string): Promise<T> {
  const url = `${API_BASE_URL}${path}`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new ApiError(response.status, `API 요청 실패: ${response.status} ${response.statusText} (${url})`);
  }

  // 배치 미실행 등으로 바디가 비어있을 수 있음(204 No Content) — response.json()이
  // 빈 문자열에 대해 던지는 파싱 예외를 막기 위해 텍스트로 먼저 확인.
  const text = await response.text();
  if (text.length === 0) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}
