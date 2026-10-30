package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopApiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Shop-local error boundary. Upstream messages, causes and credential-bearing URLs never reach users. */
@RestControllerAdvice(basePackages="cn.iocoder.yudao.module.tk.controller.admin.tiktokshop")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TkTiktokShopExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<CommonResult<Object>> conflict(ResponseStatusException ignored){return error(HttpStatus.CONFLICT,"Shop task version or idempotency input changed; refresh the task or create a new task");}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<CommonResult<Object>> invalid(IllegalArgumentException ignored){return error(HttpStatus.BAD_REQUEST,"Shop selection or video is invalid, expired, changed or inaccessible; select an available product and an owned MP4/MOV/WEBM video, then check the title and AI disclosure");}
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<CommonResult<Object>> unavailable(IllegalStateException ignored){return error(HttpStatus.CONFLICT,"Shop authorization or configuration is unavailable; reconnect the creator account or contact the administrator");}
    @ExceptionHandler(TkTiktokShopApiException.class) public ResponseEntity<CommonResult<Object>> upstream(TkTiktokShopApiException e){return error(e.isReauthorizationRequired()?HttpStatus.CONFLICT:HttpStatus.BAD_GATEWAY,e.isReauthorizationRequired()?"TikTok Shop requires creator authorization again; reconnect the account":"TikTok Shop could not complete this request; check the saved task status before attempting another publication");}
    private ResponseEntity<CommonResult<Object>> error(HttpStatus status,String message){return ResponseEntity.status(status).body(CommonResult.error(status.value(),message));}
}
