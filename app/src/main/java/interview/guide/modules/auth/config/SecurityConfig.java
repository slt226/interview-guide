package interview.guide.modules.auth.config;

import interview.guide.modules.auth.security.JwtAuthenticationFilter;
import interview.guide.modules.auth.service.JwtService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1) 容器内部的转发，不是用户请求，必须放行
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        // 2) 跨域预检
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 3) 登录注册
                        .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                        // 4) 接口文档
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 5) 健康检查（监控用，只开这两个，别整个 /actuator/**）
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        // 6) WebSocket 握手：HTTP 层放行，真正鉴权在握手拦截器里
                        .requestMatchers("/ws/**").permitAll()
                        // 7) 其余全部要登录
                        .anyRequest().authenticated()
                )
                // 没认证时返回 401 JSON，而不是默认的空响应
                .exceptionHandling(eh -> eh.authenticationEntryPoint(this::writeUnauthorized))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeUnauthorized(HttpServletRequest request,
                                   HttpServletResponse response,
                                   AuthenticationException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":401,\"message\":\"未登录或登录已过期\",\"success\":false,\"data\":null}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
