package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPublishTaskDO;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class TkTiktokShopWorkflowClientTest {
    ObjectMapper json=new ObjectMapper();TkTiktokShopApiClient api=mock(TkTiktokShopApiClient.class);
    @Test void exactPrecheckAndPublishContracts()throws Exception {
        TkTiktokShopWorkflowClient c=new TkTiktokShopWorkflowClient(api,json);TkTiktokShopPublishTaskDO t=new TkTiktokShopPublishTaskDO();t.setFileId("f");t.setTitle("caption");t.setProductId("p");t.setProductLinkTitle("Tea 2");t.setIsAiGenerated(true);
        when(api.post(anyString(),anyString(),any())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"precheck\":{\"task_id\":\"pc\"},\"video\":{\"id\":\"vid\"}}"),"request"));
        assertEquals("pc",c.createPrecheck(t,"token"));
        verify(api).post(eq("/affiliate_creator/202511/videos/precheck_task"),eq("token"),argThat(body->{JsonNode n=json.valueToTree(body);return n.path("video_info").size()==1&&"f".equals(n.path("video_info").path("file_id").asText())&&"p".equals(n.path("product_link_info").path("product_id").asText());}));
        assertEquals("vid",c.publish(t,"token").getVideoId());
        verify(api).post(eq("/affiliate_creator/202603/videos"),eq("token"),argThat(body->{JsonNode n=json.valueToTree(body);return n.path("video_info").path("is_ai_generated").asBoolean()&&"caption".equals(n.path("video_info").path("title").asText())&&n.size()==2;}));
    }
    @Test void violationRequiredQualityFailureAdvisoryUnknownNeverPasses()throws Exception{
        TkTiktokShopWorkflowClient c=new TkTiktokShopWorkflowClient(api,json);
        for(String[] pair:new String[][]{{"SUCCESS","FAIL"},{"FAIL","SUCCESS"},{"UNKNOWN","SUCCESS"},{"SUCCESS","PROCESSING"}}){
            when(api.get(anyString(),anyString(),anyMap())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"precheck_task\":{\"violation_check_result\":{\"status\":\""+pair[0]+"\"},\"good_quality_check_result\":{\"status\":\""+pair[1]+"\"}}}"),null));
            TkTiktokShopWorkflowClient.Check check=c.check("pc","token");assertEquals("SUCCESS".equals(pair[0])&&!"PROCESSING".equals(pair[1]),check.isPassed());
            assertEquals("FAIL".equals(pair[1]),check.isQualityAdvisory());
        }
    }
    @Test void successEnvelopeMissingVideoIdIsUnknownFailure()throws Exception{
        TkTiktokShopWorkflowClient c=new TkTiktokShopWorkflowClient(api,json);
        for(String data:new String[]{"{}","{\"video\":{\"id\":\"bad/id\"}}"}){
            when(api.post(anyString(),anyString(),any())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree(data),"safe-request"));
            TkTiktokShopApiException error=assertThrows(TkTiktokShopApiException.class,()->c.publish(new TkTiktokShopPublishTaskDO(),"token"));
            assertEquals("INVALID_RESPONSE",error.getCode());assertEquals("safe-request",error.getRequestId());assertNull(error.getCause());
        }
    }
    @Test void officialPrecheckIssuesRetainViolationRiskAndQualitySuggestions()throws Exception{
        TkTiktokShopWorkflowClient c=new TkTiktokShopWorkflowClient(api,json);
        when(api.get(anyString(),anyString(),anyMap())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{\"precheck_task\":{\"violation_check_result\":{\"status\":\"FAIL\",\"issues\":[{\"risk\":\"Pirated Content\",\"suggestions\":\"Creating original content is essential.\",\"raw\":\"omit\"}]},\"good_quality_check_result\":{\"status\":\"FAIL\",\"issues\":[{\"code\":\"LOW_CONTENT_PROMOTIO\",\"suggestions\":\"Showcase the product from multiple perspectives.\"}]}}}"),null));
        TkTiktokShopWorkflowClient.Check check=c.check("pc","token");JsonNode saved=json.valueToTree(check);
        assertTrue(check.isFailed());assertFalse(check.isPassed());assertTrue(check.isQualityAdvisory());
        assertEquals("Pirated Content",saved.path("violationIssues").path(0).path("risk").asText());
        assertEquals("Creating original content is essential.",saved.path("violationIssues").path(0).path("suggestions").asText());
        assertEquals("LOW_CONTENT_PROMOTIO",saved.path("qualityIssues").path(0).path("code").asText());
        assertEquals("Showcase the product from multiple perspectives.",saved.path("qualityIssues").path(0).path("suggestions").asText());
        assertFalse(saved.toString().contains("omit"));
    }
    @Test void precheckIssuesAreBoundedAllowlistedPlainText()throws Exception{
        TkTiktokShopWorkflowClient c=new TkTiktokShopWorkflowClient(api,json);
        com.fasterxml.jackson.databind.node.ObjectNode data=json.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode result=data.putObject("precheck_task").putObject("violation_check_result");result.put("status","FAIL");
        com.fasterxml.jackson.databind.node.ArrayNode issues=result.putArray("issues");
        for(int i=0;i<25;i++)issues.addObject().put("code","bad/code").put("risk","Risk\u0000\r\n"+String.join("",Collections.nCopies(300,"x"))).put("suggestions",String.join("",Collections.nCopies(1100,"y"))).put("access_token","never-copy");
        issues.add("ignore-non-object");
        when(api.get(anyString(),anyString(),anyMap())).thenReturn(new TkTiktokShopApiClient.Result(data,null));
        JsonNode saved=json.valueToTree(c.check("pc","token"));JsonNode safe=saved.path("violationIssues");
        assertEquals(20,safe.size());assertEquals(0,saved.path("qualityIssues").size());
        assertTrue(safe.path(0).path("code").isNull());assertTrue(safe.path(0).path("risk").asText().length()<=256);assertTrue(safe.path(0).path("suggestions").asText().length()<=1024);
        assertFalse(safe.path(0).path("risk").asText().chars().anyMatch(Character::isISOControl));assertFalse(saved.toString().contains("never-copy"));
    }
}
