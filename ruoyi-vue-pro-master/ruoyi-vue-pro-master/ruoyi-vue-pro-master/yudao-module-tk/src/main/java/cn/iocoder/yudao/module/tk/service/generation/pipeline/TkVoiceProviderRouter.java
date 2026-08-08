package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TkVoiceProviderRouter {

    private final Map<String, TkVoiceTtsClient> clients = new HashMap<>();

    public TkVoiceProviderRouter(List<TkVoiceTtsClient> clients) {
        for (TkVoiceTtsClient client : clients) {
            String provider = TkTtsProviderEnum.normalize(client.provider());
            if (this.clients.put(provider, client) != null) {
                throw new IllegalStateException("Duplicate voice provider: " + provider);
            }
        }
    }

    public TkVoiceTtsClient resolve(String provider) {
        String normalized = TkTtsProviderEnum.normalize(provider);
        TkVoiceTtsClient client = clients.get(normalized);
        if (client == null) {
            throw new IllegalStateException("Voice provider is unavailable: " + normalized);
        }
        return client;
    }
}
