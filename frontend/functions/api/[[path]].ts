/**
 * Cloudflare Pages Function — `/api/*` 요청을 백엔드(Render)로 리버스 프록시한다.
 *
 * 왜 필요한가: 프론트(Cloudflare)와 백엔드(Render)가 서로 다른 도메인이면 세션/CSRF
 * 쿠키가 "교차 사이트(서드파티) 쿠키"가 되어, (1) 브라우저가 새로고침 후 쿠키를
 * 전송/유지하지 못해 로그아웃되고 (2) 프론트 JS가 다른 도메인의 XSRF-TOKEN 쿠키를
 * document.cookie로 읽지 못해 즐겨찾기 POST가 CSRF 403으로 실패한다.
 *
 * 이 프록시를 두면 브라우저는 프론트와 "같은 오리진"으로만 통신하고, 백엔드가 내려준
 * 쿠키(Domain 속성 없음 = host-only)는 프론트 도메인에 붙어 1st-party가 된다 → 세션
 * 유지·CSRF·서드파티 차단 문제가 한 번에 사라진다.
 *
 * 백엔드 주소는 코드에 하드코딩하지 않고 Cloudflare Pages 환경변수 BACKEND_URL로
 * 주입한다(예: https://spotscore-xxxx.onrender.com, 뒤 슬래시 없이).
 */
interface Env {
  BACKEND_URL: string;
}

export async function onRequest(context: {
  request: Request;
  env: Env;
}): Promise<Response> {
  const { request, env } = context;

  if (!env.BACKEND_URL) {
    return new Response(
      JSON.stringify({ error: 'CONFIG_ERROR', message: 'BACKEND_URL 환경변수가 설정되지 않았습니다.' }),
      { status: 500, headers: { 'Content-Type': 'application/json' } },
    );
  }

  const incoming = new URL(request.url);
  const backend = env.BACKEND_URL.replace(/\/$/, '');
  // incoming.pathname은 /api/v1/... 형태이고 백엔드도 같은 경로로 서비스하므로 그대로 붙인다.
  const target = `${backend}${incoming.pathname}${incoming.search}`;

  const headers = new Headers(request.headers);
  // Host는 target URL에서 다시 세팅되게 두고, Origin은 서버-사이드 호출엔 의미가 없으면서
  // 백엔드 CORS를 불필요하게 트리거하므로 제거한다(같은 오리진이라 CORS 자체가 불필요).
  headers.delete('host');
  headers.delete('origin');

  const method = request.method.toUpperCase();
  const hasBody = method !== 'GET' && method !== 'HEAD';

  const proxied = new Request(target, {
    method,
    headers,
    body: hasBody ? await request.arrayBuffer() : undefined,
    // Set-Cookie/상태코드가 소실되지 않도록 리다이렉트는 그대로 클라이언트에 전달한다.
    redirect: 'manual',
  });

  return fetch(proxied);
}
