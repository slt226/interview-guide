package interview.guide.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体。
 * 长度上限与 app_user 表列宽严格对齐：username varchar(50)。
 * 密码策略：6~64 位，且至少包含一个英文字母（与主流站点一致）。
 * 注意：该策略只作用于注册。登录接口不做策略校验，否则历史上已注册的
 * 弱密码账号会被自己的密码挡在门外。
 */
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须在 3~50 之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须在 6~64 之间")
        @Pattern(regexp = "(?s).*[A-Za-z].*", message = "密码必须包含至少一个英文字母")
        String password
) {
}
