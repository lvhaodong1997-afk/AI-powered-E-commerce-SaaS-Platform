package cn.iocoder.yudao.module.tk.service.reference;

public interface TkTranscriptTextVerifyService {

    TkTranscriptTextVerifyResult verify(String transcriptText, String segmentsJson);

}
