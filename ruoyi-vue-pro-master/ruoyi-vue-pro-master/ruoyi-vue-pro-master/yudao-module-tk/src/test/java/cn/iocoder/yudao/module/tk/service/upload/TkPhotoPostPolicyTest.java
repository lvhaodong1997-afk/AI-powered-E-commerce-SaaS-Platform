package cn.iocoder.yudao.module.tk.service.upload;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
class TkPhotoPostPolicyTest {
    @Test void photoPolicyBindsPrivateAclExactKeyTypeAndSize() {
        TkOssPostPolicySigner.Policy policy=TkOssPostPolicySigner.signPrivatePhoto("id","secret","tk/open-api/c/original/u",123L,300,"image/png",null, Clock.systemUTC());
        String json=new String(Base64.getDecoder().decode(policy.getPolicy()),StandardCharsets.UTF_8);
        assertTrue(json.contains("[\"eq\",\"$key\",\"tk/open-api/c/original/u\"]"));
        assertTrue(json.contains("[\"eq\",\"$x-oss-object-acl\",\"private\"]"));
        assertTrue(json.contains("[\"eq\",\"$Content-Type\",\"image/png\"]"));
        assertTrue(json.contains("[\"content-length-range\",123,123]"));
    }
    @Test void aclFieldUsesDocumentedJsonName() throws Exception {
        cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokMediaVO.OssFields fields=
                new cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokMediaVO.OssFields("p","id","s",null);
        fields.setXOssObjectAcl("private");
        com.fasterxml.jackson.databind.JsonNode json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields));
        assertEquals("private",json.path("xOssObjectAcl").asText());assertFalse(json.has("xossObjectAcl"));
    }
}
