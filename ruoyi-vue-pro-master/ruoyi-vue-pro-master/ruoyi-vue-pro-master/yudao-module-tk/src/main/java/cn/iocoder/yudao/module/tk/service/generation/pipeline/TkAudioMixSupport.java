package cn.iocoder.yudao.module.tk.service.generation.pipeline;

/** Versioned audio controls: existing requests and tasks retain their original gain semantics. */
public final class TkAudioMixSupport {
    private TkAudioMixSupport() {
    }

    public static int normalizeVersion(Integer version) {
        if (version == null || version == 1) {
            return 1;
        }
        if (version == 2) {
            return 2;
        }
        throw new IllegalArgumentException("不支持的音频混音版本");
    }

    public static double normalizeVolume(Double volume, Integer version) {
        int selectedVersion = normalizeVersion(version);
        if (volume == null) {
            return 0.10D;
        }
        if (!Double.isFinite(volume)) {
            throw new IllegalArgumentException("背景音乐音量必须是有效数字");
        }
        return selectedVersion == 2
                ? Math.max(0D, Math.min(1D, volume))
                : Math.max(0.01D, Math.min(0.30D, volume));
    }
}
