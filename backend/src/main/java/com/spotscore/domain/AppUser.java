package com.spotscore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 일반 사용자 계정. 즐겨찾기(관심 지역x업종)를 서버에 영속화하기 위해 도입한
 * 세션 기반 로그인의 주체다(V13 마이그레이션). 테이블명은 PostgreSQL 예약어를
 * 피해 app_user로 둔다.
 *
 * passwordHash는 반드시 BCrypt 해시(평문 저장 금지)이며, 인코딩/검증은 전적으로
 * 보안 계층(BCryptPasswordEncoder + AuthenticationManager)에서만 다룬다 - 이
 * 엔티티는 저장소 역할만 한다.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AppUser() {
    }

    public AppUser(String email, String passwordHash, String displayName, LocalDateTime createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
