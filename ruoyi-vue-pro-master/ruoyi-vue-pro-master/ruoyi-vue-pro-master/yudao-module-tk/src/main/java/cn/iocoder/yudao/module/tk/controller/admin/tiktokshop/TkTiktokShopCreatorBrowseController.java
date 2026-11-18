package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopCreatorBrowseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tk/tiktok-shop/accounts/{id}/creator")
public class TkTiktokShopCreatorBrowseController {
    private final TkTiktokShopCreatorBrowseService creator;

    @GetMapping("/{view}")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:query') and @ss.hasPermission('tk:tiktok-shop-creator:query')")
    public CommonResult<TkTiktokShopCreatorBrowseService.Page> list(
            @PathVariable("id") Long id, @PathVariable("view") String view,
            @RequestParam(value = "pageToken", required = false) String pageToken,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return success(creator.list(id, view, pageToken, keyword));
    }

    @PostMapping("/showcase/{productId}")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:query') and @ss.hasPermission('tk:tiktok-shop-creator:showcase')")
    public CommonResult<Boolean> add(@PathVariable("id") Long id, @PathVariable("productId") String productId) {
        creator.updateShowcase(id, productId, true);
        return success(true);
    }

    @DeleteMapping("/showcase/{productId}")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:query') and @ss.hasPermission('tk:tiktok-shop-creator:showcase')")
    public CommonResult<Boolean> remove(@PathVariable("id") Long id, @PathVariable("productId") String productId) {
        creator.updateShowcase(id, productId, false);
        return success(true);
    }
}
