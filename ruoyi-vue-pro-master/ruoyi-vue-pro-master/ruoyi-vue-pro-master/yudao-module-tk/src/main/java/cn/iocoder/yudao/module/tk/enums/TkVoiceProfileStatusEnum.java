package cn.iocoder.yudao.module.tk.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TkVoiceProfileStatusEnum {

    CLONING("CLONING"),
    READY("READY"),
    FAILED("FAILED"),
    DISABLED("DISABLED");

    private final String status;

}
