package interview.guide.modules.auth.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String username
) {
}
