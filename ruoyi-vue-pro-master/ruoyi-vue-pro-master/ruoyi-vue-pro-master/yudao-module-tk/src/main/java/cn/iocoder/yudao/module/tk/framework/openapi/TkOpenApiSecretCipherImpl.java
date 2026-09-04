package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokTokenCipher;
import org.springframework.stereotype.Component;

@Component
public class TkOpenApiSecretCipherImpl implements TkOpenApiSecretCipher {

    private final TkTiktokTokenCipher tokenCipher;

    public TkOpenApiSecretCipherImpl(TkTiktokTokenCipher tokenCipher) {
        this.tokenCipher = tokenCipher;
    }

    @Override
    public String encrypt(String value) {
        return tokenCipher.encrypt(value);
    }

    @Override
    public String decrypt(String value) {
        return tokenCipher.decrypt(value);
    }
}
