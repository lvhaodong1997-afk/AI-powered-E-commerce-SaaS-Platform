package cn.iocoder.yudao.module.tk.framework.openapi;

public interface TkOpenApiSecretCipher {
    String encrypt(String value);
    String decrypt(String value);
}
