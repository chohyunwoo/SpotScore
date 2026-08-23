package com.spotscore.favorite;

import com.spotscore.domain.AppUser;
import com.spotscore.domain.Favorite;
import com.spotscore.domain.IndustryCategory;
import com.spotscore.domain.Region;
import com.spotscore.dto.FavoriteResponse;
import com.spotscore.exception.ResourceNotFoundException;
import com.spotscore.repository.AppUserRepository;
import com.spotscore.repository.FavoriteRepository;
import com.spotscore.repository.IndustryCategoryRepository;
import com.spotscore.repository.RegionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자별 즐겨찾기(관심 지역x업종) 관리. 모든 조회/삭제는 반드시 userId로 소유권을
 * 확인해, 다른 사용자의 즐겨찾기를 보거나 지울 수 없게 한다.
 */
@Service
public class FavoriteService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final AppUserRepository appUserRepository;
    private final RegionRepository regionRepository;
    private final IndustryCategoryRepository industryCategoryRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, AppUserRepository appUserRepository,
                           RegionRepository regionRepository, IndustryCategoryRepository industryCategoryRepository) {
        this.favoriteRepository = favoriteRepository;
        this.appUserRepository = appUserRepository;
        this.regionRepository = regionRepository;
        this.industryCategoryRepository = industryCategoryRepository;
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> list(Long userId) {
        return favoriteRepository.findByUserIdWithRegionAndIndustry(userId).stream()
                .map(FavoriteResponse::from)
                .toList();
    }

    /**
     * 즐겨찾기 추가. 이미 같은 조합이 있으면 새로 만들지 않고 기존 것을 그대로 반환한다
     * (멱등) - 프론트에서 별표를 여러 번 눌러도 409가 아니라 일관된 성공 응답을 받게.
     * 존재하지 않는 지역/업종 코드는 404로 막는다.
     */
    @Transactional
    public FavoriteResponse add(Long userId, String regionCode, String industryCode) {
        return favoriteRepository
                .findByUser_IdAndRegion_RegionCodeAndIndustry_IndustryCode(userId, regionCode, industryCode)
                .map(FavoriteResponse::from)
                .orElseGet(() -> {
                    AppUser user = appUserRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
                    Region region = regionRepository.findById(regionCode)
                            .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 지역 코드입니다: " + regionCode));
                    IndustryCategory industry = industryCategoryRepository.findById(industryCode)
                            .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 업종 코드입니다: " + industryCode));

                    Favorite saved = favoriteRepository.save(
                            new Favorite(user, region, industry, LocalDateTime.now()));
                    log.info("즐겨찾기 추가 - userId: {}, regionCode: {}, industryCode: {}",
                            userId, regionCode, industryCode);
                    return FavoriteResponse.from(saved);
                });
    }

    @Transactional
    public void delete(Long userId, Long favoriteId) {
        long deleted = favoriteRepository.deleteByIdAndUser_Id(favoriteId, userId);
        if (deleted == 0) {
            // 대상이 없거나 남의 즐겨찾기 - 둘을 구분하지 않고 404로 통일(소유권 노출 방지).
            throw new ResourceNotFoundException("삭제할 즐겨찾기를 찾을 수 없습니다.");
        }
        log.info("즐겨찾기 삭제 - userId: {}, favoriteId: {}", userId, favoriteId);
    }
}
