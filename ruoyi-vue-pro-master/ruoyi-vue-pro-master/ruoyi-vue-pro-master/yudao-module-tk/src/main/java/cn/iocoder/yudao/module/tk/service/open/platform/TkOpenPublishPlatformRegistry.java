package cn.iocoder.yudao.module.tk.service.open.platform;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class TkOpenPublishPlatformRegistry {
    private final Map<String, TkOpenPublishPlatformAdapter> adapters = new HashMap<>();

    public TkOpenPublishPlatformRegistry(List<TkOpenPublishPlatformAdapter> adapterList) {
        for (TkOpenPublishPlatformAdapter adapter : adapterList) {
            adapters.put(adapter.platform().toUpperCase(Locale.ROOT), adapter);
        }
    }

    public TkOpenPublishPlatformAdapter getRequired(String platform) {
        TkOpenPublishPlatformAdapter adapter = adapters.get(platform.toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported publish platform: " + platform);
        }
        return adapter;
    }
}
