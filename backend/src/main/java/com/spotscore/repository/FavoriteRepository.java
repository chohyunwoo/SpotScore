package com.spotscore.repository;

import com.spotscore.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // 즐겨찾기 목록은 항상 "이 사용자의 전체"를 최신순으로 조회한다. region/industry는
    // 응답 DTO가 이름을 함께 내려주므로 N+1을 피하려 JOIN FETCH로 함께 로딩한다.
    @Query("SELECT f FROM Favorite f JOIN FETCH f.region JOIN FETCH f.industry " +
            "WHERE f.user.id = :userId ORDER BY f.createdAt DESC")
    List<Favorite> findByUserIdWithRegionAndIndustry(@Param("userId") Long userId);

    // 중복 저장 방지 및 멱등 추가(이미 있으면 그대로 반환)에 쓴다.
    Optional<Favorite> findByUser_IdAndRegion_RegionCodeAndIndustry_IndustryCode(
            Long userId, String regionCode, String industryCode);

    // 삭제는 반드시 "본인 소유"임을 함께 확인해야 남의 즐겨찾기를 지우지 못한다.
    // 삭제된 행 수를 반환해, 대상이 없거나 남의 것이면(0건) 404로 구분한다.
    long deleteByIdAndUser_Id(Long id, Long userId);
}
