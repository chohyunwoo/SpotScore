package com.spotscore.collector.geo;

import java.util.List;

/**
 * GeoJSON Polygon/MultiPolygon의 centroid(면적 중심)를 순수 자바로 계산한다.
 *
 * 선택 근거: 꼭짓점을 단순 평균하는 방식은 구현은 제일 간단하지만, 꼭짓점이
 * 몰려있는 쪽으로 결과가 쏠린다(행정동처럼 굴곡이 많은 폴리곤에서는 실제
 * 중심에서 눈에 띄게 벗어날 수 있음). 대신 다각형의 signed area 공식(Shoelace
 * formula)으로 면적 가중 centroid를 계산한다 - 수식 자체는 표준 공식이고
 * 외부 라이브러리 없이 몇 줄로 구현 가능해서, 이 한 번의 centroid 계산만을
 * 위해 JTS 전체를 의존성으로 추가하지 않았다. 좌표계 변환(EPSG:5179 -> WGS84)
 * 은 반대로 실제 측지 투영 수식이 필요해 proj4j를 쓴다(Epsg5179ToWgs84Transformer
 * 참고) - "직접 구현 가능한 정도"의 기준을 다르게 적용한 것.
 *
 * Polygon은 첫 ring이 외곽선, 나머지는 구멍(hole)이라는 GeoJSON(RFC 7946) 규약을
 * 따른다고 가정한다 - 행정동 경계에 구멍이 있는 경우는 실질적으로 없어 이 가정이
 * 깨져도 결과에 미치는 영향은 미미하다.
 */
public final class PolygonCentroidCalculator {

    private PolygonCentroidCalculator() {
    }

    public record Point(double x, double y) {
    }

    private record RingMoment(double signedArea, double cx, double cy) {
    }

    private static RingMoment ringMoment(List<double[]> ring) {
        double area = 0;
        double cx = 0;
        double cy = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            double[] p0 = ring.get(i);
            double[] p1 = ring.get((i + 1) % n);
            double cross = p0[0] * p1[1] - p1[0] * p0[1];
            area += cross;
            cx += (p0[0] + p1[0]) * cross;
            cy += (p0[1] + p1[1]) * cross;
        }
        area /= 2.0;
        if (area == 0) {
            // 퇴화한(면적 0) ring - 첫 점을 그대로 대표점으로 사용
            return new RingMoment(0, ring.get(0)[0], ring.get(0)[1]);
        }
        return new RingMoment(area, cx / (6 * area), cy / (6 * area));
    }

    /**
     * @param rings 첫 번째가 외곽선, 이후는 구멍(hole)
     */
    public static Point polygonCentroid(List<List<double[]>> rings) {
        double totalArea = 0;
        double sumCx = 0;
        double sumCy = 0;
        for (List<double[]> ring : rings) {
            if (ring.size() < 3) {
                continue;
            }
            RingMoment moment = ringMoment(ring);
            totalArea += moment.signedArea();
            sumCx += moment.cx() * moment.signedArea();
            sumCy += moment.cy() * moment.signedArea();
        }
        if (totalArea == 0) {
            throw new IllegalArgumentException("면적이 0인 폴리곤의 centroid는 계산할 수 없음");
        }
        return new Point(sumCx / totalArea, sumCy / totalArea);
    }

    /**
     * MultiPolygon의 각 part는 서로 분리된 영역이므로, 각 part의 centroid를
     * 면적으로 가중 평균한다.
     */
    public static Point multiPolygonCentroid(List<List<List<double[]>>> polygons) {
        double totalArea = 0;
        double sumCx = 0;
        double sumCy = 0;
        for (List<List<double[]>> polygon : polygons) {
            if (polygon.isEmpty()) {
                continue;
            }
            Point centroid = polygonCentroid(polygon);
            double area = Math.abs(ringMoment(polygon.get(0)).signedArea());
            totalArea += area;
            sumCx += centroid.x() * area;
            sumCy += centroid.y() * area;
        }
        if (totalArea == 0) {
            throw new IllegalArgumentException("면적이 0인 MultiPolygon의 centroid는 계산할 수 없음");
        }
        return new Point(sumCx / totalArea, sumCy / totalArea);
    }
}
