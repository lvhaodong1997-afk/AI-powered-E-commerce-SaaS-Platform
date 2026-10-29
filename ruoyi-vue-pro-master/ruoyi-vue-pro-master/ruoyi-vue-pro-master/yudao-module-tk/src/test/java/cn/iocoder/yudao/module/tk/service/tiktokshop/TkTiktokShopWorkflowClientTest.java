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
        when(api.post(anyString(),anyString(),any())).thenReturn(new TkTiktokShopApiClient.Result(json.readTree("{}"),"r"));
        assertThrows(TkTiktokShopApiException.class,()->c.publish(new TkTiktokShopPublishTaskDO(),"token"));
    }
}
