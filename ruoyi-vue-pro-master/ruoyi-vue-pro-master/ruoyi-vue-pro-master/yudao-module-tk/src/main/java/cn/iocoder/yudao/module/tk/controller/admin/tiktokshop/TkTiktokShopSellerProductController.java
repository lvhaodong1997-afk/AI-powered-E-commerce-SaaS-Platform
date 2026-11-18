package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerProductWriteService;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerProductWriteService.CreateRequest;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerProductWriteService.WriteProblem;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/tiktok-shop/seller/shops/{shopId}/product-writing")
@PreAuthorize("@ss.hasPermission('tk:tiktok-shop-account:authorize')")
public class TkTiktokShopSellerProductController {
    private final TkTiktokShopSellerProductWriteService products;

    public TkTiktokShopSellerProductController(TkTiktokShopSellerProductWriteService products) { this.products = products; }

    @GetMapping("/capabilities")
    public CommonResult<Map<String,Boolean>> capabilities(@PathVariable("shopId") Long id) {
        return success(products.capabilities(id));
    }

    @GetMapping("/categories")
    public CommonResult<List<Map<String,String>>> categories(@PathVariable("shopId") Long id, @RequestParam("keyword") String keyword) {
        return success(products.categories(id, keyword));
    }

    @GetMapping("/categories/{categoryId}")
    public CommonResult<Map<String,Object>> category(@PathVariable("shopId") Long id, @PathVariable("categoryId") String categoryId) {
        return success(products.category(id, categoryId));
    }

    @GetMapping("/warehouses")
    public CommonResult<List<Map<String,String>>> warehouses(@PathVariable("shopId") Long id) {
        return success(products.warehouses(id));
    }

    @PostMapping("/images")
    @ApiAccessLog(enable=false)
    public CommonResult<Map<String,String>> image(@PathVariable("shopId") Long id, @RequestParam("file") MultipartFile file) throws IOException {
        if(file.getSize() > 10 * 1024 * 1024) return CommonResult.error(400, "INVALID_IMAGE");
        return success(products.uploadImage(id, file.getBytes()));
    }

    @PostMapping("/products")
    @ApiAccessLog(requestEnable=false)
    public CommonResult<Map<String,Object>> create(@PathVariable("shopId") Long id, @RequestBody CreateRequest request) {
        return success(products.create(id, request));
    }

    @GetMapping("/products/{productId}")
    public CommonResult<Map<String,Object>> product(@PathVariable("shopId") Long id, @PathVariable("productId") String productId) {
        return success(products.product(id, productId));
    }

    @PostMapping("/products/{productId}/price")
    public CommonResult<Map<String,Object>> price(@PathVariable("shopId") Long id, @PathVariable("productId") String productId,
            @RequestBody PriceRequest request) {
        return success(products.update(id, productId, request.skuId, request.currency, request.expected, request.price));
    }

    @ExceptionHandler(WriteProblem.class)
    public CommonResult<Object> rejected(WriteProblem problem) { return CommonResult.error(409, problem.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<Object> invalid() { return CommonResult.error(400, "INVALID_SELLER_PRODUCT_REQUEST"); }

    @Getter @Setter public static class PriceRequest {
        private String skuId, currency, expected, price;
    }
}
