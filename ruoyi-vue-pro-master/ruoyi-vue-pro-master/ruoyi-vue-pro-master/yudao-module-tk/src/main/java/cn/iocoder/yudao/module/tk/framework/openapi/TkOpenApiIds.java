package cn.iocoder.yudao.module.tk.framework.openapi;

import java.util.UUID;

public final class TkOpenApiIds {

    private TkOpenApiIds() {
    }

    public static String next(String prefix) {
        if (prefix == null || !prefix.matches("[a-z][a-z0-9_]{1,15}")) {
            throw new IllegalArgumentException("Invalid open API id prefix");
        }
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

}
