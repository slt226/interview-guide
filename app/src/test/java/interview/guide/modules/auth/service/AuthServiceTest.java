package interview.guide.modules.auth.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.dto.LoginRequest;
import interview.guide.modules.auth.dto.LoginResponse;
import interview.guide.modules.auth.dto.RegisterRequest;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试。
 * <p>
 * 不启动 Spring 容器、不连数据库：Repository 和 JwtService 都是 Mock，
 * 只有 PasswordEncoder 用真实实现（因为要验证"密码确实被加密了"）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    /** 用真实编码器：加密行为本身也是被测逻辑的一部分 */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // 字段顺序对应 @RequiredArgsConstructor 生成的构造器
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功：返回 token 与用户信息，token 由 JwtService 按 id 签发")
    void login_success_returnsToken() {
        UserEntity user = existingUser(1L, "test01", "123456");
        when(userRepository.findByUsername("test01")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(1L, "test01")).thenReturn("mocked.jwt.token");
        when(jwtService.getExpirationSeconds()).thenReturn(7200L);

        LoginResponse response = authService.login(new LoginRequest("test01", "123456"));

        assertEquals("mocked.jwt.token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(7200L, response.expiresIn());
        assertEquals(1L, response.userId());
        assertEquals("test01", response.username());
        // 签发时用的必须是数据库里的 id 和 username，不能是请求里传的
        verify(jwtService).generateToken(1L, "test01");
    }

    @Test
    @DisplayName("用户不存在：抛 13002，且不签发任何 Token")
    void login_userNotFound_throwsInvalidCredentials() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("nobody", "123456")));

        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
        // 关键：不能给一个不存在的用户签发 token
        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("密码错误：抛 13002，错误码与「用户不存在」完全一致（防用户名枚举）")
    void login_wrongPassword_throwsSameCodeAsUserNotFound() {
        UserEntity user = existingUser(1L, "test01", "123456");
        when(userRepository.findByUsername("test01")).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("test01", "wrong-password")));

        // 两个分支必须返回同一个 code：否则攻击者能靠错误码判断用户名是否注册过
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getMessage(), ex.getMessage());
        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }

    @Test
    @DisplayName("用户名前后空格会被 trim 后再查询，密码空格不会被 trim")
    void login_trimsUsernameButNotPassword() {
        when(userRepository.findByUsername("test01")).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("  test01  ", " 123456 ")));

        // 查询用的是 trim 后的用户名
        verify(userRepository).findByUsername("test01");
    }

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册成功：入库的密码是 BCrypt 哈希，不是明文")
    void register_storesHashedPassword() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        authService.register(new RegisterRequest("newuser", "123456"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());
        UserEntity saved = captor.getValue();

        assertEquals("newuser", saved.getUsername());
        assertNotEquals("123456", saved.getPassword(), "密码不能以明文入库");
        assertTrue(saved.getPassword().startsWith("$2a$"), "应为 BCrypt 哈希");
        assertEquals(60, saved.getPassword().length());
        // 存进去的哈希必须能被原密码校验通过
        assertTrue(passwordEncoder.matches("123456", saved.getPassword()));
    }

    @Test
    @DisplayName("注册重名：抛 13001，且不写库")
    void register_duplicateUsername_throws() {
        when(userRepository.existsByUsername("test01")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(new RegisterRequest("test01", "123456")));

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS.getCode(), ex.getCode());
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    // ==================== 工具方法 ====================

    /** 造一个"数据库里已存在"的用户，密码存的是真实哈希 */
    private UserEntity existingUser(Long id, String username, String rawPassword) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return user;
    }
}
