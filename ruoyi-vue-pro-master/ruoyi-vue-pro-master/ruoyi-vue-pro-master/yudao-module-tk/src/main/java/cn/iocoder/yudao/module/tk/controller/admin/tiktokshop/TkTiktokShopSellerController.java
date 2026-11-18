package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerReadService;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerReadService.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/tiktok-shop/seller")
public class TkTiktokShopSellerController {
    private final TkTiktokShopSellerReadService read;
    public TkTiktokShopSellerController(TkTiktokShopSellerReadService read){this.read=read;}

    @GetMapping("/operations")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-seller:query')")
    public CommonResult<Page> operations(@RequestParam("shopId") Long shopId,
            @RequestParam("view") View view,@RequestParam(value="cursor",required=false) String cursor){
        if(view.isAnalytics())throw new IllegalArgumentException("Invalid Shop operations view");
        return success(read.query(shopId,view,cursor,null,null,null));
    }

    @GetMapping("/analytics")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-analytics:query')")
    public CommonResult<Page> analytics(@RequestParam("shopId") Long shopId,
            @RequestParam("view") View view,@RequestParam(value="cursor",required=false) String cursor,
            @RequestParam("start") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value="liveAccountType",required=false) String liveAccountType){
        if(!view.isAnalytics())throw new IllegalArgumentException("Invalid Shop analytics view");
        return success(read.query(shopId,view,cursor,start,end,liveAccountType));
    }
}
