package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPublishTaskDO;
import lombok.*;
import java.time.LocalDateTime;

public final class TkTiktokShopPublishVO {
    private TkTiktokShopPublishVO(){}
    @Getter @Setter public static class CreateRequest {private Long accountId,generationTaskId,mediaId;private String title,productId,productSource="SHOWCASE",selectionProof,productLinkTitle,idempotencyKey;private Boolean isAiGenerated;}
    @Getter @Setter public static class ConfirmRequest {private Boolean confirm;private Integer version;private String requestHash;}
    /** Only public workflow facts. Never serialize entities, private paths, lease owners or product proofs. */
    @Getter public static class TaskResponse {
        private final Long id,accountId,generationTaskId,mediaId,fileSize,confirmedBy;
        private final String title,productId,productSource,productLinkTitle,stage,requestHash,precheckResult,videoId,requestId,failCode,failReason;
        private final Integer version,confirmedVersion;private final Boolean isAiGenerated;
        private final LocalDateTime confirmedAt,createTime,updateTime;
        public TaskResponse(TkTiktokShopPublishTaskDO t){id=t.getId();accountId=t.getAccountId();generationTaskId=t.getGenerationTaskId();mediaId=t.getMediaId();fileSize=t.getFileSize();title=t.getTitle();productId=t.getProductId();productSource=t.getProductSource();productLinkTitle=t.getProductLinkTitle();stage=t.getStage();requestHash=t.getRequestHash();precheckResult=t.getPrecheckResult();videoId=t.getVideoId();requestId=t.getRequestId();failCode=t.getFailCode();failReason=t.getFailReason();version=t.getVersion();confirmedVersion=t.getConfirmedVersion();confirmedBy=t.getConfirmedBy();confirmedAt=t.getConfirmedAt();isAiGenerated=t.getIsAiGenerated();createTime=t.getCreateTime();updateTime=t.getUpdateTime();}
    }
}
