package com.spotscore.collector.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PolygonCentroidCalculatorTest {

    @Test
    void computesCentroidOfAxisAlignedSquare() {
        List<List<double[]>> square = List.of(List.of(
                new double[] {0, 0}, new double[] {10, 0}, new double[] {10, 10}, new double[] {0, 10}
        ));

        PolygonCentroidCalculator.Point centroid = PolygonCentroidCalculator.polygonCentroid(square);

        assertThat(centroid.x()).isCloseTo(5.0, within(1e-9));
        assertThat(centroid.y()).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void weightsAwayFromNaiveVertexAverageForAnLShape() {
        // L자 모양: 단순 꼭짓점 평균은 (2.5, 2.5) 근처가 나오지만, 실제 면적 중심은
        // 큰 사각형 쪽으로 쏠려야 한다 - 이 차이가 vertex-average 대신 면적 가중
        // centroid를 쓴 이유다.
        List<List<double[]>> lShape = List.of(List.of(
                new double[] {0, 0}, new double[] {4, 0}, new double[] {4, 1},
                new double[] {1, 1}, new double[] {1, 4}, new double[] {0, 4}
        ));

        PolygonCentroidCalculator.Point centroid = PolygonCentroidCalculator.polygonCentroid(lShape);
        double naiveAverageX = 10.0 / 6;

        assertThat(centroid.x()).isLessThan(naiveAverageX);
    }

    @Test
    void weightsMultiPolygonPartsByArea() {
        // 큰 파트(10x10, 면적 100, centroid (5,5))와 작은 파트(2x2, 면적 4, centroid (101,101))
        // -> 면적 가중 평균 = (100*5 + 4*101) / 104 = 8.6923...
        List<List<List<double[]>>> multiPolygon = List.of(
                List.of(List.of(new double[] {0, 0}, new double[] {10, 0}, new double[] {10, 10}, new double[] {0, 10})),
                List.of(List.of(new double[] {100, 100}, new double[] {102, 100}, new double[] {102, 102}, new double[] {100, 102}))
        );

        PolygonCentroidCalculator.Point centroid = PolygonCentroidCalculator.multiPolygonCentroid(multiPolygon);

        double expected = (100 * 5.0 + 4 * 101.0) / 104.0;
        assertThat(centroid.x()).isCloseTo(expected, within(1e-6));
        assertThat(centroid.y()).isCloseTo(expected, within(1e-6));
        // 단순 파트-centroid 평균((5+101)/2=53)보다 큰 파트 쪽에 훨씬 가까워야 함
        assertThat(centroid.x()).isLessThan(53.0);
    }
}
