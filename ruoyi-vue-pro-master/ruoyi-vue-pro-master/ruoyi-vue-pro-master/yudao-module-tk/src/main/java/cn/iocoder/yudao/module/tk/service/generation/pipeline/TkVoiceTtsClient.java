package cn.iocoder.yudao.module.tk.service.generation.pipeline;

public interface TkVoiceTtsClient {

    String provider();

    String audioFormat();

    byte[] synthesize(TkVoiceSynthesisRequest request);

}
