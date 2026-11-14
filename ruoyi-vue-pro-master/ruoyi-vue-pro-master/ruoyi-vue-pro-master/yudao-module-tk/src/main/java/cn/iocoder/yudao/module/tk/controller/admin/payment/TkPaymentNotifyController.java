package cn.iocoder.yudao.module.tk.controller.admin.payment;

import java.util.Map;
import java.util.LinkedHashMap;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.service.payment.TkPaymentService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.security.PermitAll;

@RestController
@RequestMapping("/tk/payments")
public class TkPaymentNotifyController {
    private final TkPaymentService service;
    public TkPaymentNotifyController(TkPaymentService service){this.service=service;}
    @PostMapping("/notify") @PermitAll @TenantIgnore
    public Map<String,Object> notify(@RequestHeader("X-Clipforge-Timestamp") String timestamp,
            @RequestHeader("X-Clipforge-Nonce") String nonce,@RequestHeader("X-Clipforge-Signature") String signature,@RequestBody String body){
        service.notify(timestamp,nonce,signature,body);
        Map<String,Object> acknowledgement=new LinkedHashMap<>();
        acknowledgement.put("code",200);acknowledgement.put("data",true);return acknowledgement;
    }
}
