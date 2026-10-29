package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPublishTaskDO;
import com.fasterxml.jackson.databind.*;
import lombok.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Exact creator video wire contracts and safe, normalized status responses. */
@Component
public class TkTiktokShopWorkflowClient {
    private final TkTiktokShopApiClient api;private final ObjectMapper json;
    public TkTiktokShopWorkflowClient(TkTiktokShopApiClient api,ObjectMapper json){this.api=api;this.json=json;}
    public String createPrecheck(TkTiktokShopPublishTaskDO t,String token){Map<String,Object> body=body(t,false);return TkTiktokShopUploadService.requiredId(api.post("/affiliate_creator/202511/videos/precheck_task",token,body).getData().path("precheck"),"task_id");}
    public Check check(String id,String token){JsonNode node=api.get("/affiliate_creator/202601/videos/precheck_tasks/"+validId(id),token,Collections.emptyMap()).getData().path("precheck_task");return new Check(status(node.path("violation_check_result").path("status")),status(node.path("good_quality_check_result").path("status")),node.has("good_quality_check_result"));}
    public Published publish(TkTiktokShopPublishTaskDO t,String token){TkTiktokShopApiClient.Result result=api.post("/affiliate_creator/202603/videos",token,body(t,true));return new Published(TkTiktokShopUploadService.requiredId(result.getData().path("video"),"id"),result.getRequestId());}
    public String status(String id,String token){return status(api.get("/affiliate_creator/202509/videos/"+validId(id)+"/status",token,Collections.emptyMap()).getData().path("video").path("post_status"));}
    private Map<String,Object> body(TkTiktokShopPublishTaskDO t,boolean publish){Map<String,Object> video=new LinkedHashMap<>();video.put("file_id",t.getFileId());if(publish){video.put("title",t.getTitle());video.put("is_ai_generated",Boolean.TRUE.equals(t.getIsAiGenerated()));}Map<String,Object> product=new LinkedHashMap<>();product.put("product_id",t.getProductId());product.put("title",t.getProductLinkTitle());Map<String,Object> body=new LinkedHashMap<>();body.put("video_info",video);body.put("product_link_info",product);return body;}
    private static String status(JsonNode n){String s=n.asText();return Arrays.asList("SUCCESS","FAIL","PROCESSING").contains(s)?s:"UNKNOWN";}
    private static String validId(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("Invalid Shop resource identifier");return id;}
    @Getter @AllArgsConstructor public static class Published {private final String videoId,requestId;}
    @Getter @AllArgsConstructor public static class Check {
        private final String violationStatus,qualityStatus;private final boolean qualityPresent;
        public boolean isPassed(){return "SUCCESS".equals(violationStatus)&&(!qualityPresent||"SUCCESS".equals(qualityStatus)||"FAIL".equals(qualityStatus));}
        public boolean isFailed(){return "FAIL".equals(violationStatus);}
        public boolean isQualityAdvisory(){return "FAIL".equals(qualityStatus);}
    }
}
