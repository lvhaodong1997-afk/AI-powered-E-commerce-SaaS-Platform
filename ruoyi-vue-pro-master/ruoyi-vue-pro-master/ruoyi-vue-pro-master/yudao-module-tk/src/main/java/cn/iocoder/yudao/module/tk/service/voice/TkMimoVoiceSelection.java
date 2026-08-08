package cn.iocoder.yudao.module.tk.service.voice;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TkMimoVoiceSelection {

    private String mode;
    private String code;
    private String prompt;
    private String sampleUrl;

}
