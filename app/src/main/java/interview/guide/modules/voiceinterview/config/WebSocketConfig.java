package interview.guide.modules.voiceinterview.config;

import interview.guide.common.config.CorsProperties;
import interview.guide.modules.auth.service.JwtService;
import interview.guide.modules.voiceinterview.handler.VoiceInterviewWebSocketHandler;
import interview.guide.modules.voiceinterview.security.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceInterviewWebSocketHandler voiceInterviewWebSocketHandler;
    private final CorsProperties corsProperties;
    private final JwtService jwtService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceInterviewWebSocketHandler, "/ws/voice-interview/{sessionId}")
                // 握手阶段校验 query 参数里的 token，通过后把 userId 写入 session attributes
                .addInterceptors(new JwtHandshakeInterceptor(jwtService))
                .setAllowedOrigins(corsProperties.getAllowedOrigins().split(","));
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(2 * 1024 * 1024);
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024);
        return container;
    }
}
