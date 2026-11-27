package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static cn.iocoder.yudao.module.tk.service.channel.TkChannelOAuthClient.*;

@Component
@RequiredArgsConstructor
public class TkXPhotoClient {
    private final TkChannelHttp http;
    private final TkXPhotoStorage storage;

    public String publish(String token, String text, List<Path> photos, Runnable beforePost) {
        if (photos.isEmpty() || photos.size() > 4) throw new IllegalArgumentException("One to four images required");
        String auth = bearer(token);
        ObjectNode post = TkChannelHttp.JSON.createObjectNode();
        if (text != null && !text.isEmpty()) post.put("text", text);
        ArrayNode ids = post.putObject("media").putArray("media_ids");
        for (Path photo : photos) {
            byte[] bytes = storage.read(photo);
            ObjectNode upload = TkChannelHttp.JSON.createObjectNode().put("media_category", "tweet_image")
                    .put("media", Base64.getEncoder().encodeToString(bytes));
            JsonNode data = http.json("POST", X_API + "/media/upload", auth, upload, false).path("data");
            ids.add(id(data, false));
        }
        beforePost.run();
        return id(http.json("POST", X_API + "/tweets", auth, post, true).path("data"), true);
    }

    private static String id(JsonNode data, boolean uncertain) {
        String id = data.path("id").asText();
        if (!id.matches("[0-9]{1,24}")) throw new TkSocialPlatformException("X_RESPONSE_INVALID",
                "X did not return a valid ID", false, false, uncertain);
        return id;
    }
}
