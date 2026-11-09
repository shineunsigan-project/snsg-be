package com.shineunsigan.snsg.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import lombok.RequiredArgsConstructor;

/**
 * 공개 경로는 여기에만 적는다. API를 만들 때 <b>HTTP 메서드와 경로를 정확히</b> 지정해 하나씩 연다.
 * {@code /api/v1/auth/**}처럼 prefix 전체를 열면 안 된다.
 * 예로 {@code POST /api/v1/auth/tokens}(재발급)는 공개지만
 * {@code DELETE /api/v1/auth/tokens}(로그아웃)는 인증이 필요하다. 경로가 같고 메서드만 다르다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/*/authorize", "/api/v1/auth/*/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/signup/session").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/regions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activity-categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/place-candidates").permitAll()
                        // 정확한 경로만 연다. 하위 경로(/participating, /hosted)는 인증이 필요하다
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedules").permitAll()
                        // 순서가 중요하다. 아래 {scheduleId} 패턴은 /participating·/hosted와도 맞으므로 두 목록을 먼저 막는다
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedules/participating", "/api/v1/schedules/hosted")
                        .authenticated()
                        // 일정 상세. 토큰이 선택이다. 있으면 보는 사람의 역할과 버튼이 달라진다. 하위 경로는 걸리지 않는다
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedules/{scheduleId}").permitAll()
                        // 토큰이 선택이다. 있으면 본인의 현재 닉네임을 사용 가능으로 본다
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/nickname-availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/signup/profile-image",
                                "/api/v1/auth/tokens").permitAll()
                        // 카카오가 호출한다. 인증 대신 어드민 키로 발신자를 확인한다
                        .requestMatchers(HttpMethod.POST, "/webhooks/kakao/unlink").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
