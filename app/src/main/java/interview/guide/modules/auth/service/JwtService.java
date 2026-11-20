package interview.guide.modules.auth.service;

import interview.guide.modules.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * JWT 签发与校验。
 * <p>
 * 只负责「把用户标识塞进签名串」和「从签名串里把用户标识安全地取出来」，
 * 不查数据库、不涉及 Spring Security。
 */
@Service
@Slf4j
public class JwtService {

    /** HS256 要求密钥至少 256 bit */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET 未配置，无法签发/校验 Token（请检查 .env 或环境变量）");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET 长度不足：HS256 要求至少 32 字节，当前 " + bytes.length + " 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMillis = properties.getExpirationMinutes() * 60_000L;
        log.info("JwtService 初始化完成，Token 有效期 {} 分钟", properties.getExpirationMinutes());
    }

    /**
     * 签发 Token。
     *
     * @param userId   用户主键，放进标准字段 subject
     * @param username 用户名，放进自定义字段，省一次查库
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 Token。
     * 签名不对、格式错误、已过期，统一返回 empty，由调用方决定怎么响应。
     */
    public Optional<AuthUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AuthUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // 过期、签名不匹配、格式非法都会走到这里，不值得打 error 日志
            log.debug("Token 校验失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Token 有效期（秒），登录响应里返回给前端 */
    public long getExpirationSeconds() {
        return expirationMillis / 1000;
    }

    /** 从 Token 里解析出来的最小用户信息 */
    public record AuthUser(Long userId, String username) {
    }
}
