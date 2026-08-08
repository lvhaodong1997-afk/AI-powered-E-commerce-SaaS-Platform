package cn.iocoder.yudao.module.tk.service.tiktok;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class TkTiktokAuthCallbackResult {

    private boolean success;

    private String message;

}
