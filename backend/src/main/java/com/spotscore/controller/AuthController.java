package com.spotscore.controller;

import com.spotscore.auth.AuthService;
import com.spotscore.dto.LoginRequest;
import com.spotscore.dto.RegisterRequest;
import com.spotscore.dto.UserResponse;
import com.spotscore.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 일반 사용자 인증. 회원가입/로그인/내 정보는 여기서 처리하고, 로그아웃은 Spring
 * Security의 logout 필터(SecurityConfig)가 /api/v1/auth/logout에서 처리한다.
 * 인증은 세션 + HttpOnly 쿠키(JSESSIONID) 방식이다.
 */
@Tag(name = "Auth", description = "일반 사용자 회원가입/로그인/세션 관리")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    // 로그인 성공 시 인증 정보를 세션(HttpSession)에 저장해 이후 요청에서 재사용한다.
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, AuthenticationManager authenticationManager) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    @Operation(summary = "회원가입", description = "이메일 중복 시 409를 반환한다.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("요청 수신 - endpoint: /api/v1/auth/register, email: {}", request.email());
        return authService.register(request);
    }

    @Operation(summary = "로그인", description = "성공 시 세션을 생성하고 JSESSIONID 쿠키를 내려준다. " +
            "자격 증명이 틀리면 401을 반환한다.")
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        log.info("요청 수신 - endpoint: /api/v1/auth/login, email: {}", request.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        log.info("로그인 성공 - userId: {}", principal.getId());
        return new UserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName());
    }

    @Operation(summary = "내 정보 조회", description = "로그인된 세션의 사용자 정보를 반환한다(비로그인 시 401).")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return new UserResponse(principal.getId(), principal.getEmail(), principal.getDisplayName());
    }
}
