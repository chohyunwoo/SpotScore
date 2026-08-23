package com.spotscore.security;

import com.spotscore.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 이메일로 계정을 찾아 인증 주체(AppUserPrincipal)로 변환한다. AuthenticationManager가
 * 로그인 시 이 서비스로 사용자를 로딩한 뒤 BCryptPasswordEncoder로 비밀번호를 대조한다.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return appUserRepository.findByEmail(email)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 계정입니다: " + email));
    }
}
