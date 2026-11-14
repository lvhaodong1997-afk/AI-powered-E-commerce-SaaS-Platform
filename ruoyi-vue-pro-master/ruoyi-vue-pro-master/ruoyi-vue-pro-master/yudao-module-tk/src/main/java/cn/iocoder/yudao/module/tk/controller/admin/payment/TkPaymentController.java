package cn.iocoder.yudao.module.tk.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.service.payment.TkPaymentService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/tk")
public class TkPaymentController {
    private final TkPaymentService service;
    public TkPaymentController(TkPaymentService service){this.service=service;}
    @GetMapping("/recharge/packages") public CommonResult<ObjectNode> catalog(){return success(service.catalog());}
    @PostMapping("/payments/quote") public CommonResult<ObjectNode> quote(@RequestBody ObjectNode body){return success(service.quote(body));}
    @PostMapping("/payments/{orderNo}/pay") public CommonResult<ObjectNode> pay(@PathVariable String orderNo,@RequestBody ObjectNode body){return success(service.pay(orderNo,body));}
    @GetMapping("/payments/{orderNo}") public CommonResult<ObjectNode> get(@PathVariable String orderNo){return success(service.get(orderNo));}
    @GetMapping("/payments") public CommonResult<ObjectNode> page(@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="10") int pageSize){return success(service.page(pageNo,pageSize));}
    @PostMapping("/payments/{orderNo}/cancel") public CommonResult<ObjectNode> cancel(@PathVariable String orderNo){return success(service.cancel(orderNo));}
    @PostMapping("/payments/{orderNo}/refund") public CommonResult<ObjectNode> refund(@PathVariable String orderNo){return success(service.refund(orderNo));}
}
