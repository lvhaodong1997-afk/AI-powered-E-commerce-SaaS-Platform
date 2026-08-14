package cn.iocoder.yudao.module.tk.framework.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "tk.generation")
public class TkGenerationProperties {

    private Gemini gemini = new Gemini();
    private DashScope dashscope = new DashScope();
    private Mimo mimo = new Mimo();
    private Ffmpeg ffmpeg = new Ffmpeg();
    private ReferenceDownload referenceDownload = new ReferenceDownload();
    private RenderDownload renderDownload = new RenderDownload();
    private Subtitle subtitle = new Subtitle();
    private Cleanup cleanup = new Cleanup();
    private Queue queue = new Queue();
    private Upload upload = new Upload();
    private String prompt = "你是 TikTok 跨境电商短视频编导。请基于用户给出的对标视频链接、素材库名称、类目、场景和标签，输出可直接用于口播配音的中文带货脚本。要求：1. 前3秒用强钩子承接黄金三秒环节，用户未上传开头视频时系统会从 S1_HOOK 素材池随机选择完整视频；2. 后续环节从对应素材池随机选择完整视频拼接，超出目标时长时按环节压缩；3. 按卖点、场景证明、信任背书、行动号召组织；4. 语言短句化，适合字幕；5. 输出纯文案，不要解释。";

    @Data
    public static class Gemini {

        private String apiKey;
        private String baseUrl = "https://yunwu.ai/v1";
        private String textModel = "gemini-3.1-flash-lite-preview";
        private Integer timeoutSeconds = 90;

    }

    @Data
    public static class DashScope {

        private String apiKey;
        private String ttsUrl = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer";
        private String ttsModel = "cosyvoice-v3.5-plus";
        private String voice;
        private String format = "mp3";
        private Integer sampleRate = 24000;
        private Integer volume = 50;
        private Double rate = 1.1;
        private Double pitch = 1.0;
        private String language = "auto";
        private String instruction = "请以自然、清晰的语气朗读,不要有换气声、吸气声、呼吸声或任何气口,句子之间不要停顿。语速为1.1倍，文字中出现的, ' 不要进行停顿";
        private Integer timeoutSeconds = 120;

    }

    @Data
    public static class Mimo {

        private String apiKey;
        private String baseUrl = "https://api.xiaomimimo.com/v1";
        private String presetModel = "mimo-v2.5-tts";
        private String voiceDesignModel = "mimo-v2.5-tts-voicedesign";
        private String voiceCloneModel = "mimo-v2.5-tts-voiceclone";
        private String format = "wav";
        private Boolean optimizeTextPreview = true;
        private String defaultVoice = "Mia";
        private Integer timeoutSeconds = 120;

    }

    @Data
    public static class Ffmpeg {

        private String ffmpegPath = "ffmpeg";
        private String ffprobePath = "ffprobe";
        private String workDir = "${java.io.tmpdir}/tk-generation";
        private String preset = "veryfast";
        private Integer clipSeconds = 3;
        private List<Integer> clipDurationPool = Arrays.asList(2, 3, 4);
        private Integer maxTargetDuration = 180;

    }

    @Data
    public static class ReferenceDownload {

        /**
         * 复用本项目内置的抖音、TikTok、B 站真实下载脚本。
         */
        private Boolean scriptEnabled = true;
        private String scriptPython = "py";
        private String scriptPath = "tools/reference-video-download/douyin_tiktok_bilibili_tool.py";
        private String scriptRepo = "tools/reference-video-download/Douyin_TikTok_Download_API";
        private Integer scriptTimeoutSeconds = 180;
        /**
         * 对标视频下载代理，例如 http://127.0.0.1:7890。
         */
        private String proxy;
        private Boolean htmlFallbackEnabled = true;
        private Integer htmlTimeoutSeconds = 20;
        private Boolean remoteUrlValidationEnabled = true;
        private Boolean blockPrivateAddress = true;
        private Integer maxRedirects = 3;
        private Long maxDownloadBytes = 500L * 1024 * 1024;
        private List<String> allowedHosts = Arrays.asList(
                "douyin.com", "iesdouyin.com", "douyinvod.com",
                "tiktok.com", "tiktokcdn.com", "byteoversea.com", "ibytedtos.com",
                "bilibili.com", "b23.tv", "bilivideo.com", "tkassetplant.fnn.net.cn");

    }

    @Data
    public static class RenderDownload {

        private String publicBaseUrl = "https://tkassetplant.fnn.net.cn";
        private String internalBaseUrl = "http://127.0.0.1:48080";
        private Integer timeoutSeconds = 180;
        private Integer maxAttempts = 3;
        private Integer maxParallelDownloads = 3;
        private Integer retryDelayMillis = 1000;

    }

    @Data
    public static class Subtitle {

        private Asr asr = new Asr();
        private Visual visual = new Visual();
        /**
         * 字幕相对音频整体提前量，降低字幕滞后感。
         */
        private Double leadSeconds = 0.20D;
        /**
         * 开启后用 FFmpeg silencedetect 检测音频开头静音，并计入字幕提前补偿。
         */
        private Boolean detectLeadingSilenceEnabled = true;
        /**
         * 固定提前 + 开头静音补偿的最大值，避免误判导致字幕过早。
         */
        private Double maxLeadSeconds = 0.50D;
        private Double silenceNoiseDb = -35D;
        private Double silenceMinDurationSeconds = 0.08D;

    }

    @Data
    public static class Cleanup {

        private Boolean enabled = true;
        /**
         * 每小时第 5 分钟执行一次，避免所有服务都卡在整点。
         */
        private String cron = "0 5 * * * ?";
        private Integer generatedVideoRetentionHours = 24;
        private Integer referenceVideoRetentionHours = 24;
        private Integer renderWorkDirRetentionHours = 24;
        private Integer businessLogRetentionDays = 30;
        private Integer batchSize = 200;
        private Boolean dryRun = false;

    }

    @Data
    public static class Queue {

        private Integer workerSize = 2;
        private Integer queueCapacity = 100;
        private Integer scanDelayMs = 10000;
        private Integer staleSeconds = 300;
        private Integer batchSize = 10;

    }

    @Data
    public static class Upload {

        private String storageType = "local";
        private String rootDir = "${java.io.tmpdir}/tk-uploads";
        private String publicBaseUrl = "/uploads";
        private Integer chunkSizeBytes = 8 * 1024 * 1024;
        private Long maxFileSizeBytes = 100L * 1024 * 1024;
        private Integer sessionExpireHours = 24;
        private Oss oss = new Oss();

    }

    @Data
    public static class Oss {

        private Boolean enabled = false;
        private String bucket;
        private String endpoint;
        private String region;
        private String publicBaseUrl;
        private String accessKeyId;
        private String accessKeySecret;
        private Integer policyExpireSeconds = 1800;
        private Integer readUrlExpireSeconds = 0;
        private String uploadPathPrefix = "tk";

    }

    @Data
    public static class Asr {

        /**
         * 开启后调用外部 ASR 命令，命令需输出统一字幕时间轴 JSON。
         */
        private Boolean enabled = false;
        private String python = "py";
        private String scriptPath = "tools/subtitle/asr_faster_whisper.py";
        private String model = "small";
        private Boolean retryEnabled = true;
        private String retryModel = "medium";
        private Boolean estimatedFallbackOnMismatch = true;
        private Integer timeoutSeconds = 300;
        /**
         * ASR 文本和原始文案的最低相似度，低于该值时回退文案估算时间线。
         */
        private Double minTextSimilarity = 0.55D;

    }

    @Data
    public static class Visual {

        /**
         * 开启后调用外部视觉检测命令，命令需输出画面主体分析 JSON。
         */
        private Boolean enabled = false;
        private String python = "py";
        private String scriptPath = "tools/subtitle/visual_yolo_detect.py";
        private String modelPath;
        private Double frameIntervalSeconds = 1.0D;
        private Integer timeoutSeconds = 300;

    }

}
