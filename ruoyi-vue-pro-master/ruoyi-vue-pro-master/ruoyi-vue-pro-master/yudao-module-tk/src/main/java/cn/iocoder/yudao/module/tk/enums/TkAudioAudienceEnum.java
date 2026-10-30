package cn.iocoder.yudao.module.tk.enums;

public enum TkAudioAudienceEnum {
    SILVER("银发经济"),
    GEN_Z("Z世代经济"),
    WOMEN("她经济（女性消费）"),
    SINGLE("单身经济"),
    PET("宠物经济"),
    MATERNAL("母婴经济"),
    EMOTIONAL("情绪消费"),
    LOWER_TIER("下沉市场消费"),
    MIDDLE_CLASS("中产品质消费"),
    HIGH_NET_WORTH("高净值消费");
    private final String label;

    TkAudioAudienceEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static void validate(String code) {
        try {
            valueOf(code);
        } catch (Exception ex) {
            throw new IllegalArgumentException("无效的目标人群");
        }
    }
}
