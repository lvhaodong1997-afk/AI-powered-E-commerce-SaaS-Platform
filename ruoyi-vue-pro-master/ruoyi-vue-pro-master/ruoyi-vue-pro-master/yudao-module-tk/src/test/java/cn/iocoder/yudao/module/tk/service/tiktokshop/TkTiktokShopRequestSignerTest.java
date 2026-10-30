package cn.iocoder.yudao.module.tk.service.tiktokshop;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TkTiktokShopRequestSignerTest {
    private static final String PATH = "/authorization/202309/shops";
    private static final String SECRET = "e59af819cc";

    @Test
    void matchesOfficialSignatureVector() {
        assertEquals("b596b73e0cc6de07ac26f036364178ab16b0a907af13d43f0a0cd2345f582dc8",
                TkTiktokShopRequestSigner.sign(PATH, Map.of("app_key", "29a39d", "timestamp", "1623812664"), null, null, SECRET));
    }

    @Test
    void sortsParametersAndExcludesSignAndAccessTokenWithoutMutatingInput() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("timestamp", "1623812664");
        query.put("sign", "old-sign");
        query.put("access_token", "sensitive-value");
        query.put("app_key", "29a39d");
        assertEquals("b596b73e0cc6de07ac26f036364178ab16b0a907af13d43f0a0cd2345f582dc8",
                TkTiktokShopRequestSigner.sign(PATH, query, new byte[0], null, SECRET));
        assertEquals(4, query.size());
    }

    @Test
    void signsExactUtf8BodyBytesAndDecodedQueryValues() {
        assertEquals("6034096e45674b50ac63c980f725a5741b3ae313ffd3f585c2ef1448360770f5",
                TkTiktokShopRequestSigner.sign("/video/202309/a%20b", Map.of("q", "a b+中"),
                        "{\"title\": \"中\"}\n".getBytes(StandardCharsets.UTF_8), "application/json; charset=UTF-8", SECRET));
        assertNotEquals(TkTiktokShopRequestSigner.sign(PATH, Map.of(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json", SECRET),
                TkTiktokShopRequestSigner.sign(PATH, Map.of(), "{ \"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json", SECRET));
    }

    @Test
    void excludesMultipartBodyEvenWithBoundaryAndMixedCase() {
        assertEquals(TkTiktokShopRequestSigner.sign(PATH, Map.of(), null, null, SECRET),
                TkTiktokShopRequestSigner.sign(PATH, Map.of(), new byte[]{0, 1, 2, 3},
                        "Multipart/Form-Data; boundary=----example", SECRET));
    }

    @Test
    void rejectsMissingSecretAndNonPathInput() {
        assertThrows(IllegalArgumentException.class, () -> TkTiktokShopRequestSigner.sign(PATH, Map.of(), null, null, " "));
        assertThrows(IllegalArgumentException.class, () -> TkTiktokShopRequestSigner.sign("https://example.com/api", Map.of(), null, null, SECRET));
    }
}
