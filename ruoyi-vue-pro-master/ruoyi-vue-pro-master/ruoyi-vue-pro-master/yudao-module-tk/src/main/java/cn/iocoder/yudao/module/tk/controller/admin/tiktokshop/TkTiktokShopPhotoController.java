package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopPhotoService;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopPhotoService.*;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk/tiktok-shop/photos")
public class TkTiktokShopPhotoController {
    private final TkTiktokShopPhotoService photos;
    public TkTiktokShopPhotoController(TkTiktokShopPhotoService photos){this.photos=photos;}

    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:create') && @ss.hasPermission('tk:tiktok-shop-publish:confirm')")
    @ApiAccessLog(enable=false)
    public CommonResult<PhotoResponse> publish(@RequestPart("request") PhotoRequest request,@RequestPart("files") List<MultipartFile> files){
        return success(photos.publish(request,files));
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:query')")
    public CommonResult<PageResult<PhotoResponse>> history(@RequestParam("accountId") Long accountId,@RequestParam(value="pageNo",defaultValue="1") int pageNo){
        return success(photos.history(accountId,pageNo));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermission('tk:tiktok-shop-publish:query')")
    public CommonResult<PhotoResponse> status(@RequestParam("accountId") Long accountId,@PathVariable("id") Long id){return success(photos.status(accountId,id));}
}
