package interview.guide.modules.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 BCrypt 加密器的三个核心特性。
 * <p>
 * 不启动 Spring 容器（没有 {@code @SpringBootTest}），直接用 new 出来的实例，
 * 所以整个测试类是毫秒级的。
 */
class PasswordEncoderTest {

    /** 与 SecurityConfig 中 PasswordEncoder Bean 的构造方式保持一致 */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("同一明文两次加密的结果不同：每次都用新随机盐")
    void encode_samePassword_producesDifferentHash() {
        String raw = "123456";

        String first = passwordEncoder.encode(raw);
        String second = passwordEncoder.encode(raw);

        // BCrypt 每次 encode 都会生成新的随机盐，因此同一明文也得不到相同哈希。
        // 这条断言如果哪天失败了，说明盐变成固定值 —— 加盐就失去意义了。
        assertNotEquals(first, second, "两次加密结果相同，说明盐不是随机的");
    }

    @Test
    @DisplayName("matches 校验：正确密码通过，错误密码拒绝")
    void matches_verifiesRawPasswordAgainstHash() {
        String raw = "123456";
        String storedHash = passwordEncoder.encode(raw);

        // 注意参数顺序：第一个是用户输入的明文，第二个是库里存的哈希
        assertTrue(passwordEncoder.matches(raw, storedHash), "正确密码应通过校验");
        assertFalse(passwordEncoder.matches("123457", storedHash), "错误密码必须拒绝");
        assertFalse(passwordEncoder.matches("123456 ", storedHash), "密码比对区分空白字符");
    }

    @Test
    @DisplayName("BCrypt 哈希长度固定 60 位")
    void encode_hashLengthIsSixty() {
        String hash = passwordEncoder.encode("123456");

        // 60 = $2a$ + $10$ 头部(7) + 22 位盐 + 31 位密文
        // 这正是建表时 password 列必须大于 60 字符（当初设为 varchar(100)）的原因
        assertEquals(60, hash.length(), "BCrypt 哈希长度应固定为 60");
        assertTrue(hash.startsWith("$2a$"), "哈希应以 $2a$ 开头，标识 BCrypt 版本");
    }
}
