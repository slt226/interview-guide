package interview.guide.common.security;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.auth.service.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}
    private static JwtService.AuthUser principalNull(){
        Authentication auth= SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof JwtService.AuthUser user){
            return user;
        }
        return null;
    }
    public static Long idOrNull(){
        var user = principalNull();
        return user==null ? null :user.userId();
    }
    public static String usernameOrNull(){
        var user = principalNull();
        return user==null ? null : user.username();
    }
    public static Long requireId(){
        Long id = idOrNull();
        if(id==null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

}
