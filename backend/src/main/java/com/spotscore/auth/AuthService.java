package com.spotscore.auth;

import com.spotscore.domain.AppUser;
import com.spotscore.dto.RegisterRequest;
import com.spotscore.dto.UserResponse;
import com.spotscore.exception.DuplicateResourceException;
import com.spotscore.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 회원가입 처리. 비밀번호는 BCryptPasswordEncoder로 해시해서만 저장한다(평문 금지).
 * 로그인/로그아웃은 Spring Security(AuthenticationManager + 세션)가 담당하므로 여기엔
 * 두지 않는다 - 이 서비스는 계정 생성만 책임진다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            log.warn("회원가입 실패 - 이미 사용 중인 이메일: {}", request.email());
            throw new DuplicateResourceException("이미 사용 중인 이메일입니다.");
        }
        AppUser user = new AppUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                LocalDateTime.now());
        AppUser saved = appUserRepository.save(user);
        log.info("회원가입 성공 - userId: {}, email: {}", saved.getId(), saved.getEmail());
        return UserResponse.from(saved);
    }
}
