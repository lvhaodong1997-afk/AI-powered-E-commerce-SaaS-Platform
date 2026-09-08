package cn.iocoder.yudao.module.tk.service.tiktok;

public interface TkTiktokWebhookService {

    void receive(String rawBody, String signature);

}
