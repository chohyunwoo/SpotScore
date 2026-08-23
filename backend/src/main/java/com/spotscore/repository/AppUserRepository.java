package com.spotscore.repository;

import com.spotscore.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // 로그인 시 이메일로 계정을 조회한다(email은 V13에서 UNIQUE). 회원가입 시
    // 중복 검사에도 존재 여부 확인용으로 쓴다.
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
