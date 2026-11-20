package interview.guide.modules.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册密码策略测试：6~64 位 + 至少一个英文字母。
 * 这里不启动 Spring 容器，直接跑 Bean Validation，毫秒级完成。
 */
class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** 把违规信息拼成可读字符串，断言时只关心文案 */
    private String validatePassword(String password) {
        Set<ConstraintViolation<RegisterRequest>> violations =
                validator.validate(new RegisterRequest("tester01", password));
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(" | "));
    }

    @Test
    @DisplayName("纯数字密码被拒绝，提示必须包含英文字母")
    void pureDigitPasswordRejected() {
        String message = validatePassword("123456");
        assertTrue(message.contains("密码必须包含至少一个英文字母"),
                "纯数字应被拒绝，实际提示: " + message);
    }

    @Test
    @DisplayName("字母 + 数字的常规密码通过")
    void normalPasswordAccepted() {
        assertEquals("", validatePassword("abc123"), "含字母与数字应通过");
    }

    @Test
    @DisplayName("纯字母密码通过：当前只要求含字母，未强制数字")
    void pureLetterPasswordAccepted() {
        assertEquals("", validatePassword("abcdef"), "纯字母满足现有策略");
    }

    @Test
    @DisplayName("过短且无字母时，长度与字母两条提示同时返回")
    void tooShortAndNoLetterReportsBoth() {
        String message = validatePassword("12345");
        assertTrue(message.contains("6~64"), "应提示长度不足: " + message);
        assertTrue(message.contains("英文字母"), "应提示缺少字母: " + message);
    }
}
