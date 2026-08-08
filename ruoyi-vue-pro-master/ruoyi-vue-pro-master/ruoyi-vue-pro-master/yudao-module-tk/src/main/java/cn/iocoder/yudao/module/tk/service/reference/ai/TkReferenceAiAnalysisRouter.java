package cn.iocoder.yudao.module.tk.service.reference.ai;

import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TkReferenceAiAnalysisRouter {

    private final Map<String, TkReferenceAiAnalysisClient> clients = new HashMap<>();

    public TkReferenceAiAnalysisRouter(List<TkReferenceAiAnalysisClient> clients) {
        for (TkReferenceAiAnalysisClient client : clients) {
            String provider = TkReferenceAnalysisProvider.normalize(client.provider());
            if (this.clients.put(provider, client) != null) {
                throw new IllegalStateException("Duplicate reference analysis provider: " + provider);
            }
        }
    }

    public TkReferenceAiAnalysisResult analyze(String provider, TkReferenceAiAnalysisContext context) {
        String normalized = TkReferenceAnalysisProvider.normalize(provider);
        TkReferenceAiAnalysisClient client = clients.get(normalized);
        if (client == null) {
            throw new IllegalStateException("Reference analysis provider is unavailable: " + normalized);
        }
        return client.analyze(context);
    }
}
