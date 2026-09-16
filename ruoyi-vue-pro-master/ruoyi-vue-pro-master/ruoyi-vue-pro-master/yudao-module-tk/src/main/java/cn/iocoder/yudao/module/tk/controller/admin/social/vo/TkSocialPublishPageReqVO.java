package cn.iocoder.yudao.module.tk.controller.admin.social.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TkSocialPublishPageReqVO extends PageParam {
    private String status;
    private Long taskId;
}
