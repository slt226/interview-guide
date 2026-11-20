package interview.guide.testsupport;

import interview.guide.modules.auth.service.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 单元测试用的登录态工具。
 *
 * 数据隔离改造后，Service 会通过 CurrentUser.requireId() 从 SecurityContext 取 owner。
 * 纯 Mockito 测试没有经过过滤器链，SecurityContext 是空的，需要手动放入一个 principal，
 * 否则会抛 401（未授权）。
 */
public final class TestAuthContext {

    public static final long DEFAULT_USER_ID = 1L;

    private TestAuthContext() {
    }

    /**
     * 以指定用户身份执行
     */
    public static void login(Long userId) {
        var principal = new JwtService.AuthUser(userId, "test-user-" + userId);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void login() {
        login(DEFAULT_USER_ID);
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
