package interview.guide.modules.auth;

import interview.guide.modules.auth.config.JwtProperties;
import interview.guide.modules.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 JWT 的签发与校验行为。
 * <p>
 * 不启动 Spring 容器，直接构造 JwtProperties + JwtService，毫秒级完成。
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("a-test-secret-that-is-long-enough-0123456789");
        properties.setExpirationMinutes(120);
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("签发的 Token 是标准三段式：header.payload.signature")
    void generate_producesThreeSegments() {
        String token = jwtService.generateToken(1L, "test01");

        assertEquals(3, token.split("\\.").length, "JWT 应由两个点分成三段");
    }

    @Test
    @DisplayName("签发后能解析回同样的 userId 和 username")
    void parse_returnsOriginalClaims() {
        String token = jwtService.generateToken(42L, "test01");

        Optional<JwtService.AuthUser> parsed = jwtService.parse(token);

        assertTrue(parsed.isPresent(), "自己签发的 Token 必须能解析成功");
        assertEquals(42L, parsed.get().userId());
        assertEquals("test01", parsed.get().username());
    }

    @Test
    @DisplayName("篡改签名段后验签失败：证明真的校验了签名，不是只做 Base64 解码")
    void parse_rejectsTamperedToken() {
        String token = jwtService.generateToken(1L, "test01");
        // '!' 不在 base64url 字符集里，必定破坏签名
        String tampered = token.substring(0, token.length() - 1) + "!";

        assertTrue(jwtService.parse(tampered).isEmpty(), "被篡改的 Token 必须拒绝");
    }

    @Test
    @DisplayName("换个密钥签发的 Token 无法通过本服务校验")
    void parse_rejectsTokenSignedByOtherKey() {
        JwtProperties other = new JwtProperties();
        other.setSecret("another-secret-that-is-also-long-enough-9876");
        other.setExpirationMinutes(120);
        String foreignToken = new JwtService(other).generateToken(1L, "test01");

        assertTrue(jwtService.parse(foreignToken).isEmpty(), "不同密钥签发的 Token 必须拒绝");
    }
}
