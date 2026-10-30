package cn.iocoder.yudao.module.tk.enums;
public enum TkAudioAudienceEnum {
 SILVER("银发人群"), GEN_Z("Z世代"), WOMEN("女性"), SINGLE("单身人群"), PET("宠物人群"),
 MATERNAL("母婴人群"), EMOTIONAL("情感人群"), LOWER_TIER("下沉市场"), MIDDLE_CLASS("中产人群"), HIGH_NET_WORTH("高净值人群");
 private final String label;
 TkAudioAudienceEnum(String label) { this.label=label; }
 public String getLabel() { return label; }
 public static void validate(String code) {
   try { valueOf(code); } catch (Exception ex) { throw new IllegalArgumentException("无效的目标人群"); }
 }
}
