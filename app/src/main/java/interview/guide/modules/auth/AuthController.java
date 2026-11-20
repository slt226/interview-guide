package interview.guide.modules.auth;

import interview.guide.common.annotation.RateLimit;
import interview.guide.common.result.Result;
import interview.guide.common.security.CurrentUser;
import interview.guide.modules.auth.dto.LoginRequest;
import interview.guide.modules.auth.dto.LoginResponse;
import interview.guide.modules.auth.dto.RegisterRequest;
import interview.guide.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/register")
    @RateLimit(dimension = RateLimit.Dimension.IP,count=10,interval = 1,timeUnit = RateLimit.TimeUnit.MINUTES)
    public Result<Void> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }
    @PostMapping("/login")
    @RateLimit(dimension = RateLimit.Dimension.IP,count=10,interval = 1,timeUnit = RateLimit.TimeUnit.MINUTES)
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Result.success(authService.login(request));
    }
    @GetMapping("/me")
    public Result<Map<String,Object>> me() {
        return Result.success(Map.of(
                "userId", CurrentUser.requireId(),
                "username",CurrentUser.usernameOrNull()
        )
        );
    }
}
