package com.shineunsigan.snsg.admin.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.common.exception.CommonErrorCode;
import com.shineunsigan.snsg.common.web.ApiResponse;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 경로에서 인증이 필요한데 없을 때의 401. 사용자 경로와 같은 {@code UNAUTHENTICATED} 봉투를 쓴다.
 * auth의 진입점은 다른 도메인 infrastructure라 가져다 쓸 수 없어 따로 둔다.
 */
@Component
@RequiredArgsConstructor
public class AdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(CommonErrorCode.UNAUTHENTICATED));
    }
}
