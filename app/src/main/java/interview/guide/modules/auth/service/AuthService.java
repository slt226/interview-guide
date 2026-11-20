package interview.guide.modules.auth.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.dto.LoginRequest;
import interview.guide.modules.auth.dto.LoginResponse;
import interview.guide.modules.auth.dto.RegisterRequest;
import interview.guide.modules.auth.model.UserEntity;
import interview.guide.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;        // ← 新增

    @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        log.info("新用户注册成功: id={}, username={}", user.getId(), username);
    }
    /** 只读事务：登录不改任何数据，readOnly 让 Hibernate 跳过脏检查 */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        UserEntity user =userRepository.findByUsername(username)
                .orElseThrow(()->new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if(!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        String token = jwtService.generateToken(user.getId(),user.getUsername());
        log.info("用户登录成功: id={}, username={}", user.getId(), username);
        return new LoginResponse(token,"Bearer",jwtService.getExpirationSeconds()
                ,user.getId(),user.getUsername()
                );
    }

}
