package cn.iocoder.yudao.module.tk.service.reference;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TkReferenceOpeningClip {

    private String url;
    private String name;
    private Integer startSecond;
    private Integer endSecond;

}
