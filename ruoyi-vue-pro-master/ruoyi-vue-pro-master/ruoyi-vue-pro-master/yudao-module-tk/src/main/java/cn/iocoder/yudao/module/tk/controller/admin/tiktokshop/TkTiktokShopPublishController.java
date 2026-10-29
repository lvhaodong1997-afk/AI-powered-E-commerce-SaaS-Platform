package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopPublishVO.*;
import cn.iocoder.yudao.module.tk.service.tiktokshop.*;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/tiktok-shop")
public class TkTiktokShopPublishController {
    private final TkTiktokShopPublishService publish;private final TkTiktokShopProductService products;private final TkDataScopeService scope;
    public TkTiktokShopPublishController(TkTiktokShopPublishService publish,TkTiktokShopProductService products,TkDataScopeService scope){this.publish=publish;this.products=products;this.scope=scope;}
    @GetMapping("/accounts/{id}/products")
    @PreAuthorize("@ss.hasAnyPermissions('tk:tiktok-shop-account:query','tk:tiktok-shop-publish:create')")
    public CommonResult<TkTiktokShopProductService.ProductPage> products(@PathVariable Long id,@RequestParam(defaultValue="SHOWCASE")String source,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String pageToken,@RequestParam(required=false)String titleKeyword){return success(products.list(id,source,pageSize,pageToken,titleKeyword,scope.getCurrentScope().getUserIdString()));}
    @PostMapping("/publish/tasks") @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:create')")
    public CommonResult<TaskResponse> create(@RequestBody CreateRequest request){return success(publish.create(request));}
    @GetMapping("/publish/tasks") @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:query')")
    public CommonResult<PageResult<TaskResponse>> list(@RequestParam(defaultValue="1")int pageNo,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)Long accountId){return success(publish.list(pageNo,pageSize,accountId));}
    @GetMapping("/publish/tasks/{id}") @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:query')")
    public CommonResult<TaskResponse> get(@PathVariable Long id){return success(publish.get(id));}
    @PostMapping("/publish/tasks/{id}/confirm") @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:confirm')")
    public CommonResult<TaskResponse> confirm(@PathVariable Long id,@RequestBody ConfirmRequest request){return success(publish.confirm(id,request));}
}
