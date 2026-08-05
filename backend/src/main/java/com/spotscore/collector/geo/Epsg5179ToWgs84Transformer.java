package com.spotscore.collector.geo;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.stereotype.Component;

/**
 * SGIS boundary/hadmarea.geojson 좌표를 WGS84(lat/lon)로 변환한다.
 *
 * 실제 호출로 확인한 사실: 응답 본문에는 CRS 메타데이터가 전혀 없고, 좌표값은
 * x~95만, y~194만대의 큰 숫자다(예: 강남구 역삼1동 x=958643, y=1944720).
 * 이 값은 위경도가 아니라 투영좌표계다 - EPSG:5179(Korea 2000 / Unified CS,
 * 대한민국 통계청/국토지리정보원이 쓰는 중부원점 통합좌표계, 단위 m)의 정의
 * (중앙경선 127.5E, 기준위도 38N, false easting/northing 1,000,000/2,000,000m,
 * GRS80 타원체)로 역산하면 서울 강남 위경도(약 위도 37.50, 경도 127.03) 범위에
 * 들어맞음을 실제 변환값으로 확인했다.
 *
 * proj4j 내장 EPSG 조회 테이블(버전에 따라 5179 누락 가능)에 의존하지 않도록,
 * 위 EPSG:5179 공식 정의를 proj4 파라미터 문자열로 직접 명시해서 CRS를 만든다.
 */
@Component
public class Epsg5179ToWgs84Transformer {

    private static final String EPSG_5179_PROJ4 =
            "+proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 " +
                    "+ellps=GRS80 +units=m +no_defs";

    // EPSG:4326도 이름 조회(createFromName) 대신 명시적 proj4 정의로 만든다 -
    // createFromName은 번들된 EPSG 조회 파일(proj4/nad/epsg)을 읽으려 하는데, 이
    // 애플리케이션 패키징(jar) 환경에서 해당 리소스에 접근하지 못해 실제로
    // IllegalStateException("Unable to access CRS file")이 났다. WGS84는 표준
    // 정의가 고정돼 있어 파라미터로 명시해도 모호함이 없다.
    private static final String EPSG_4326_PROJ4 = "+proj=longlat +datum=WGS84 +no_defs";

    private final CoordinateTransform transform;

    public Epsg5179ToWgs84Transformer() {
        CRSFactory crsFactory = new CRSFactory();
        CoordinateReferenceSystem sourceCrs = crsFactory.createFromParameters("EPSG:5179", EPSG_5179_PROJ4);
        CoordinateReferenceSystem targetCrs = crsFactory.createFromParameters("EPSG:4326", EPSG_4326_PROJ4);
        this.transform = new CoordinateTransformFactory().createTransform(sourceCrs, targetCrs);
    }

    /**
     * @return {latitude, longitude} (WGS84, degrees)
     */
    public double[] toWgs84(double x, double y) {
        ProjCoordinate source = new ProjCoordinate(x, y);
        ProjCoordinate target = new ProjCoordinate();
        transform.transform(source, target);
        // proj4j 변환 결과는 (경도, 위도) = (x, y) 순서로 담겨 온다.
        return new double[] {target.y, target.x};
    }
}
