package cn.iocoder.yudao.module.tk.controller.admin.social.vo;

import lombok.Data;
import javax.validation.constraints.*;
import java.util.List;

@Data
public class TkSocialPublishCreateReqVO {
    @NotEmpty @Size(max = 20)
    private List<@NotNull Long> accountIds;
    private Long mediaId;
    private Long generationTaskId;
    @NotBlank @Size(max = 255)
    private String title;
    @Size(max = 2200)
    private String instagramCaption;
    @Size(max = 5000)
    private String facebookMessage;
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,80}")
    private String idempotencyKey;
}
