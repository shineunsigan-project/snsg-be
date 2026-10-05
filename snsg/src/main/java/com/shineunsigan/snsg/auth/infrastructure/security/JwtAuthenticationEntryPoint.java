package com.shineunsigan.snsg.auth.infrastructure.security;

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
 * 인증이 필요한데 없을 때의 401. 전역 예외 처리를 타지 않으므로 여기서 직접 봉투를 씌운다.
 * 이게 없으면 본문 없는 Spring 기본 401이 나가 클라이언트가 code로 분기할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

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
