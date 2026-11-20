package interview.guide.modules.voiceinterview.security;

import interview.guide.modules.auth.service.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权。
 * <p>
 * 浏览器无法给 WebSocket 握手设置 Authorization 请求头，因此 token 只能通过
 * 握手 URL 的 query 参数传递（ws://host/ws/voice-interview/{id}?token=xxx）。
 * 这里校验通过后把 userId 放进 WebSocketSession 的 attributes，
 * Handler 里用 session.getAttributes().get("userId") 取当前用户。
 */
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");

        // 注意：token 为 null 时不能直接丢给 jwtService.parse，
        // 解析器抛的是 IllegalArgumentException 之外的类型时无法被 catch 住
        if (!StringUtils.hasText(token)) {
            log.warn("WebSocket 握手被拒：缺少 token，uri={}", request.getURI().getPath());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        return jwtService.parse(token).map(user -> {
            attributes.put(ATTR_USER_ID, user.userId());
            return true;
        }).orElseGet(() -> {
            log.warn("WebSocket 握手被拒：token 无效或已过期，uri={}", request.getURI().getPath());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        });
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 无需处理
    }
}
