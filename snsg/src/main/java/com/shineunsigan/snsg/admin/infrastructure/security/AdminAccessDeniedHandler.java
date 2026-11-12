package com.shineunsigan.snsg.admin.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.web.ApiResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 경로에서 인증은 됐지만 역할이 모자랄 때의 403. 지금 역할 검사는 MASTER 전용 경로뿐이라 {@code MASTER_ONLY}를 쓴다.
 * 401 진입점({@link AdminAuthenticationEntryPoint})과 같은 봉투를 쓴다. 없으면 Spring 기본 403(우리 봉투가 아님)이 나간다.
 */
@Component
@RequiredArgsConstructor
public class AdminAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(AdminErrorCode.MASTER_ONLY));
    }
}
