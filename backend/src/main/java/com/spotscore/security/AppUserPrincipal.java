package com.spotscore.security;

import com.spotscore.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security의 인증 주체. AppUser를 감싸 UserDetails 계약을 만족시키면서,
 * 컨트롤러가 @AuthenticationPrincipal로 바로 userId/displayName을 꺼낼 수 있도록
 * 원본 필드를 노출한다. 권한(role) 개념은 아직 없어(일반 사용자 단일 등급) 빈
 * 권한 목록을 반환한다 - admin은 별도 API Key 체계라 이 인증과 무관.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String displayName;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.displayName = user.getDisplayName();
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        // 이 서비스의 로그인 식별자는 이메일이다.
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
