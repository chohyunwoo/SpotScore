/**
 * Kakao Maps JavaScript SDK의 최소 타입 선언.
 * 공식 @types 패키지가 없어 이 프로젝트에서 실제로 사용하는 API 표면만 직접 선언함
 * (any 사용 금지 원칙 준수). SDK를 더 사용하게 되면 여기에 타입을 추가할 것.
 */
export {};

declare global {
  interface Window {
    kakao: typeof kakao;
  }

  namespace kakao.maps {
    class LatLng {
      constructor(latitude: number, longitude: number);
    }

    interface MapOptions {
      center: LatLng;
      level: number;
    }

    class Map {
      constructor(container: HTMLElement, options: MapOptions);
      setCenter(latlng: LatLng): void;
      panTo(latlng: LatLng): void;
      setLevel(level: number): void;
      setBounds(bounds: LatLngBounds): void;
    }

    /**
     * REGION엔 폴리곤 없이 centroid만 있어 지역별 경계를 알 수 없다 - 대신 랭킹에
     * 실린 모든 지역의 좌표를 감싸는 범위를 계산해 지도를 그 데이터에 맞게
     * 자동으로 프레이밍할 때 쓴다(업종 전환 시 기본 줌이 전국 단위라 수백 개
     * 지역 배지가 한 점으로 뭉쳐 보이는 문제를 막기 위함).
     */
    class LatLngBounds {
      constructor();
      extend(latlng: LatLng): void;
    }

    class Size {
      constructor(width: number, height: number);
    }

    class Point {
      constructor(x: number, y: number);
    }

    interface MarkerImageOptions {
      offset?: Point;
    }

    /** 개별 업소 점(작은 원) 아이콘용 - 지역 배지(CustomOverlay)와 구분되는 스타일. */
    class MarkerImage {
      constructor(src: string, size: Size, options?: MarkerImageOptions);
    }

    interface MarkerOptions {
      position: LatLng;
      map?: Map;
      title?: string;
      image?: MarkerImage;
    }

    class Marker {
      constructor(options: MarkerOptions);
      setMap(map: Map | null): void;
      setPosition(latlng: LatLng): void;
      getPosition(): LatLng;
    }

    interface InfoWindowOptions {
      content?: string | HTMLElement;
      removable?: boolean;
      zIndex?: number;
    }

    /** 개별 업소 마커 호버/클릭 시 이름을 보여주는 용도 - 마커마다 만들지 않고 하나를 재사용한다. */
    class InfoWindow {
      constructor(options?: InfoWindowOptions);
      open(map: Map, marker?: Marker): void;
      close(): void;
      setContent(content: string | HTMLElement): void;
    }

    interface MarkerClustererOptions {
      map: Map;
      markers?: Marker[];
      averageCenter?: boolean;
      minLevel?: number;
      disableClickZoom?: boolean;
      /** 클러스터 개수 구간별 스타일(기본값은 카카오 기본 노랑/초록이라 디자인 톤과 안 맞아 오버라이드). */
      styles?: Partial<CSSStyleDeclaration>[];
    }

    /**
     * libraries=clusterer로 별도 로드되는 서브라이브러리 - 개별 업소가 지역당
     * 최대 수천 개라 CustomOverlay로 하나씩 그리면 렌더링이 눈에 띄게 느려짐
     * (실측 4초+) 확인 후 도입. addMarkers에 넘긴 마커들의 map 표시/제거를
     * 클러스터러가 대신 관리한다 - 개별 setMap 호출 불필요.
     */
    class MarkerClusterer {
      constructor(options: MarkerClustererOptions);
      addMarkers(markers: Marker[]): void;
      clear(): void;
    }

    namespace event {
      function addListener(target: Marker | Map, type: string, handler: () => void): void;
    }

    /**
     * 지역 폴리곤 경계 데이터가 없어(REGION엔 centroid만 저장, CLAUDE.md) 좌표
     * 하나에 임의 HTML(점수 배지)을 올리는 용도로만 씀 - 기본 Marker 아이콘 대신
     * scoreScale 색으로 칠한 원형 배지를 지도 위에 그린다.
     */
    interface CustomOverlayOptions {
      position: LatLng;
      content: HTMLElement;
      map?: Map;
      xAnchor?: number;
      yAnchor?: number;
      zIndex?: number;
    }

    class CustomOverlay {
      constructor(options: CustomOverlayOptions);
      setMap(map: Map | null): void;
    }

    function load(callback: () => void): void;
  }
}
