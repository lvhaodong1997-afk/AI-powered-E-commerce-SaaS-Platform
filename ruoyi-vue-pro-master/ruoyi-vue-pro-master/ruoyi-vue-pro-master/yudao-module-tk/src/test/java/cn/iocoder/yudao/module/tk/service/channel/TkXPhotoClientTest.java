package cn.iocoder.yudao.module.tk.service.channel;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkXPhotoClientTest {
    @TempDir Path temporary;

    @Test
    void validatesDecodedImagesAndRejectsIncorrectMime() throws Exception {
        Path file = temporary.resolve("image");
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
        assertDoesNotThrow(() -> TkXPhotoStorage.validate(file, "image/png"));
        assertThrows(IllegalArgumentException.class, () -> TkXPhotoStorage.validate(file, "image/jpeg"));
        Files.writeString(file, "not an image");
        assertThrows(IllegalArgumentException.class, () -> TkXPhotoStorage.validate(file, "image/png"));
    }

    @Test
    void sendsFourPhotosInOrderAndChecksAuthorizationBeforePosting() {
        TkChannelHttp http = mock(TkChannelHttp.class);
        TkXPhotoStorage storage = mock(TkXPhotoStorage.class);
        Runnable allowed = mock(Runnable.class);
        List<Path> images = List.of(Path.of("first"), Path.of("second"), Path.of("third"), Path.of("fourth"));
        when(storage.read(any())).thenReturn(new byte[]{1});
        String api = "https://api.x.com/2";
        when(http.json(eq("POST"), eq(api + "/media/upload"), eq("Bearer token"), any(), eq(false)))
                .thenReturn(result("11"), result("22"), result("33"), result("44"));
        when(http.json(eq("POST"), eq(api + "/tweets"), eq("Bearer token"), any(), eq(true))).thenReturn(result("123"));
        assertEquals("123", new TkXPhotoClient(http, storage).publish("token", "text", images, allowed));
        InOrder order = inOrder(storage, http, allowed);
        for (Path image : images) {
            order.verify(storage).read(image);
            order.verify(http).json(eq("POST"), eq(api + "/media/upload"), anyString(),
                    argThat(body -> "tweet_image".equals(((JsonNode) body).path("media_category").asText())), eq(false));
        }
        order.verify(allowed).run();
        order.verify(http).json(eq("POST"), eq(api + "/tweets"), anyString(),
                argThat(body -> "[\"11\",\"22\",\"33\",\"44\"]".equals(((JsonNode) body).path("media").path("media_ids").toString())), eq(true));
    }

    private static JsonNode result(String id) {
        return TkChannelHttp.JSON.createObjectNode().set("data", TkChannelHttp.JSON.createObjectNode().put("id", id));
    }

    @Test
    void refusesExcessAndDuplicatePhotoIds() {
        assertThrows(IllegalArgumentException.class, () -> TkXPhotoStorage.validateIds(List.of()));
        assertThrows(IllegalArgumentException.class, () -> TkXPhotoStorage.validateIds(List.of("a", "b", "c", "d", "e")));
        assertThrows(IllegalArgumentException.class, () -> TkXPhotoStorage.validateIds(List.of("a", "a")));
        assertDoesNotThrow(() -> TkXPhotoStorage.validateIds(List.of("a", "b", "c", "d")));
    }
}
