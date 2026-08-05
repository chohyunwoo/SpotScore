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
    }

    interface MarkerOptions {
      position: LatLng;
      map?: Map;
      title?: string;
    }

    class Marker {
      constructor(options: MarkerOptions);
      setMap(map: Map | null): void;
      setPosition(latlng: LatLng): void;
      getPosition(): LatLng;
    }

    namespace event {
      function addListener(target: Marker | Map, type: string, handler: () => void): void;
    }

    function load(callback: () => void): void;
  }
}
