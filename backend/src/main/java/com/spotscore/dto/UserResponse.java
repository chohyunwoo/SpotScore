package com.spotscore.dto;

import com.spotscore.domain.AppUser;

/**
 * 로그인/내 정보 응답. Entity(AppUser)를 그대로 반환하지 않고(CLAUDE.md 원칙 5)
 * passwordHash 같은 민감 필드를 제외한 최소 정보만 노출한다.
 */
public record UserResponse(Long id, String email, String displayName) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
