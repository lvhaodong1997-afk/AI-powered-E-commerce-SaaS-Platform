package cn.iocoder.yudao.module.tk.framework.openapi;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TkOpenApiPrincipal {

    private final String clientId;
    private final String clientName;
    private final String permissions;

    public boolean hasPermission(String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            return true;
        }
        String normalized = "," + (permissions == null ? "" : permissions.replace(" ", "")) + ",";
        return normalized.contains(",*,") || normalized.contains("," + permission + ",");
    }

}
