package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkGenerationBatchDetailRespVO {

    private TkGenerationBatchRespVO batch;

    private List<TkGenerationTaskSummaryRespVO> tasks;

    private List<TkGenerationStepLogRespVO> stepLogs;
}
