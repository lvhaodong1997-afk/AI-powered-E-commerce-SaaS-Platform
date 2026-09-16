package cn.iocoder.yudao.module.tk.controller.admin.social;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.tk.service.social.auth.*;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/tk/social-account")
public class TkSocialAccountController {
    private final TkSocialAccountService accounts;
    private final TkSocialAuthService auth;
    public TkSocialAccountController(TkSocialAccountService accounts,TkSocialAuthService auth) {
        this.accounts=accounts; this.auth=auth;
    }
    @GetMapping("/facebook-pages")
    @PreAuthorize("@ss.hasPermission('tk:social-account:authorize')")
    public CommonResult<List<Map<String,Object>>> facebookPages(@RequestParam("sessionId") String id) {
        return success(auth.facebookPages(id));
    }
    @PostMapping("/facebook-pages/bind")
    @PreAuthorize("@ss.hasPermission('tk:social-account:authorize')")
    public CommonResult<Boolean> bind(@Valid @RequestBody PageBindRequest request) {
        auth.bindFacebookPages(request.getSessionId(),request.getPageIds()); return success(true);
    }
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('tk:social-account:query')")
    public CommonResult<PageResult<Map<String,Object>>> page(@Valid PageParam page,
                                                            @RequestParam(value="platform",required=false) String platform) {
        return success(accounts.page(page,platform));
    }
    @PostMapping("/validate")
    @PreAuthorize("@ss.hasPermission('tk:social-account:update')")
    public CommonResult<Boolean> validate(@RequestParam("id") Long id) { accounts.validate(id); return success(true); }
    @DeleteMapping("/unbind")
    @PreAuthorize("@ss.hasPermission('tk:social-account:update')")
    public CommonResult<Boolean> unbind(@RequestParam("id") Long id) { accounts.unbind(id); return success(true); }
    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('tk:social-account:update')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) { accounts.delete(id); return success(true); }

    @Data public static class PageBindRequest {
        @NotBlank @Size(max=128) private String sessionId;
        @NotEmpty @Size(max=100) private List<@NotBlank @Pattern(regexp="[0-9]{1,100}") String> pageIds;
    }
}
