import { useEffect, useState } from 'react';

const KAKAO_SDK_SCRIPT_ID = 'kakao-maps-sdk';

export type KakaoLoaderStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * Kakao Map JS SDK를 동적으로 로드. 로깅 가이드(CLAUDE.md) —
 * "Kakao Map 렌더링 실패: warn, 지도 초기화 실패 사유"를 따름.
 */
export function useKakaoLoader(): KakaoLoaderStatus {
  const [status, setStatus] = useState<KakaoLoaderStatus>('idle');

  useEffect(() => {
    if (window.kakao?.maps) {
      setStatus('ready');
      return;
    }

    const appKey = import.meta.env.VITE_KAKAO_MAP_APP_KEY;
    if (!appKey || appKey === 'YOUR_KAKAO_JS_KEY') {
      console.warn('[MapDashboard] Kakao Map 렌더링 실패: VITE_KAKAO_MAP_APP_KEY가 설정되지 않음');
      setStatus('error');
      return;
    }

    const existingScript = document.getElementById(KAKAO_SDK_SCRIPT_ID);
    if (existingScript) {
      existingScript.addEventListener('load', () => window.kakao.maps.load(() => setStatus('ready')));
      return;
    }

    setStatus('loading');
    const script = document.createElement('script');
    script.id = KAKAO_SDK_SCRIPT_ID;
    script.async = true;
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`;
    script.onload = () => window.kakao.maps.load(() => setStatus('ready'));
    script.onerror = () => {
      console.warn('[MapDashboard] Kakao Map 렌더링 실패: SDK 스크립트 로드 실패');
      setStatus('error');
    };
    document.head.appendChild(script);
  }, []);

  return status;
}
