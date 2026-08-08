package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkGenerationBatchPageReqVO extends PageParam {

    private String keyword;

    private Long libraryId;

    private String status;
}
