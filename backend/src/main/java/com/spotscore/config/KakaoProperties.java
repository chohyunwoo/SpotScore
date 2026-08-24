package com.spotscore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kakao Local(키워드 검색) REST API 설정. 가게 상세 모달에서 "카카오맵에서 보기"를
 * 좌표 핀이 아니라 실제 등록된 장소(place) 상세로 연결하기 위해, 가게명+좌표로
 * 키워드 검색해 place_url을 얻는 데 쓴다(이슈 #34).
 *
 * <p>SGIS/상권정보/KOSIS와 달리 미설정 시 부팅을 막지 않는다 - 챗봇(Groq)과 동일하게
 * 핵심 점수 파이프라인과 무관한 선택적 부가 기능이라, 키가 없으면 프론트가 이름 검색
 * 링크로 폴백하고 나머지는 정상 동작해야 하기 때문. "실시간 외부 호출 금지" 원칙은
 * SGIS/상권정보 배치 수집에 대한 것이고, 이 조회는 챗봇처럼 요청 시점 인터랙션이 의도된
 * 별개 기능이다.
 */
@ConfigurationProperties(prefix = "spotscore.kakao")
public record KakaoProperties(String baseUrl, String restApiKey) {

    public KakaoProperties {
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://dapi.kakao.com" : baseUrl;
        restApiKey = restApiKey == null ? "" : restApiKey;
    }

    public boolean isConfigured() {
        return !restApiKey.isBlank();
    }
}
