package com.spotscore.controller;

import com.spotscore.dto.FavoriteRequest;
import com.spotscore.dto.FavoriteResponse;
import com.spotscore.favorite.FavoriteService;
import com.spotscore.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사용자별 즐겨찾기(관심 지역x업종) API. SecurityConfig에서 이 경로 전체를 인증
 * 필수로 두므로, @AuthenticationPrincipal은 항상 로그인된 사용자다(비로그인은 401).
 */
@Tag(name = "Favorite", description = "관심 지역x업종 즐겨찾기(로그인 필요)")
@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "내 즐겨찾기 목록", description = "최신순. 지역명/업종명을 함께 반환한다.")
    @GetMapping
    public List<FavoriteResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        log.info("요청 수신 - endpoint: /api/v1/favorites, userId: {}", principal.getId());
        return favoriteService.list(principal.getId());
    }

    @Operation(summary = "즐겨찾기 추가",
            description = "이미 있는 조합이면 기존 항목을 그대로 반환한다(멱등). 없는 지역/업종 코드는 404.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteResponse add(@AuthenticationPrincipal AppUserPrincipal principal,
                                @Valid @RequestBody FavoriteRequest request) {
        log.info("요청 수신 - endpoint: POST /api/v1/favorites, userId: {}, regionCode: {}, industryCode: {}",
                principal.getId(), request.regionCode(), request.industryCode());
        return favoriteService.add(principal.getId(), request.regionCode(), request.industryCode());
    }

    @Operation(summary = "즐겨찾기 삭제", description = "본인 소유가 아니거나 없는 id는 404.")
    @DeleteMapping("/{favoriteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long favoriteId) {
        log.info("요청 수신 - endpoint: DELETE /api/v1/favorites/{}, userId: {}", favoriteId, principal.getId());
        favoriteService.delete(principal.getId(), favoriteId);
    }
}
