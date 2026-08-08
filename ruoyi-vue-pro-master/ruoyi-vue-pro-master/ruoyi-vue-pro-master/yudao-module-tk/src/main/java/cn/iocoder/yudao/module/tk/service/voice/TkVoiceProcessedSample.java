package cn.iocoder.yudao.module.tk.service.voice;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TkVoiceProcessedSample {

    private byte[] content;
    private String filename;
    private String contentType;

}
