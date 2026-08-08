package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialUsagePhaseEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVideoDurationSupport;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class TkGenerationPrecheckServiceImpl implements TkGenerationPrecheckService {

    @Resource
    private TkMaterialVideoMapper materialVideoMapper;
    @Resource
    private TkGenerationProperties generationProperties;

    @Override
    public TkGenerationPrecheckRespVO precheck(TkGenerationTaskCreateReqVO createReqVO) {
        TkGenerationPrecheckRespVO result = new TkGenerationPrecheckRespVO();
        int targetDuration = TkVideoDurationSupport.normalize(createReqVO.getReferenceDuration(),
                generationProperties.getFfmpeg().getMaxTargetDuration());
        List<TkMaterialVideoDO> materials = materialVideoMapper.selectListByLibraryId(createReqVO.getLibraryId());
        int totalDuration = materials == null ? 0 : materials.stream()
                .map(TkMaterialVideoDO::getDuration)
                .filter(duration -> duration != null && duration > 0)
                .mapToInt(Long::intValue)
                .sum();
        result.getMaterialSummary().setAvailableCount(materials == null ? 0 : materials.size());
        result.getMaterialSummary().setTotalDuration(totalDuration);
        result.getMaterialSummary().setTargetDuration(targetDuration);
        fillPhaseSummary(result, materials);

        if (CollUtil.isEmpty(materials)) {
            addError(result, "MATERIAL_EMPTY", "素材库没有可用于混剪的视频，请先上传可用素材。",
                    "素材库没有可用视频", "请先上传该产品的视频素材，并将关键片段标记为产品亮相、使用演示、效果证明等用途。");
        } else {
            boolean hasInvalidUrl = materials.stream()
                    .anyMatch(item -> StrUtil.isBlank(item.getFileUrl())
                            || !(StrUtil.startWithIgnoreCase(item.getFileUrl(), "http://")
                            || StrUtil.startWithIgnoreCase(item.getFileUrl(), "https://")));
            if (hasInvalidUrl) {
                addError(result, "MATERIAL_DOWNLOAD_FAILED", "素材库存在不可下载的视频地址，请重新上传或修复素材。",
                        "部分素材无法下载", "请重新上传失败素材，或删除不可访问的视频地址后再预检。");
            }
            String routeConfig = resolveRouteConfig(createReqVO);
            if (!TkGenerationRouteConfigSupport.isFullPoolRandom(routeConfig) && totalDuration > 0 && totalDuration < targetDuration) {
                int missingDuration = targetDuration - totalDuration;
                TkGenerationPrecheckRespVO.PrecheckIssue issue = addError(result, "MATERIAL_DURATION_NOT_ENOUGH",
                        StrUtil.format("素材库可用时长不足，当前 {} 秒，目标 {} 秒。", totalDuration, targetDuration),
                        StrUtil.format("素材总时长不足，还差 {} 秒", missingDuration),
                        "请继续上传可用于混剪的视频素材，或降低目标成片时长后重新预检。");
                issue.setRequiredDuration(targetDuration);
                issue.setActualDuration(totalDuration);
                issue.setMissingDuration(missingDuration);
            }
            if (TkGenerationRouteConfigSupport.isFullPoolRandom(routeConfig)) {
                addFullPoolRandomWarnings(result, materials, targetDuration);
                addFullPoolRandomErrors(result, materials, targetDuration);
            } else {
                boolean leadGeneration = TkGeminiPromptConfig.isLeadGeneration(createReqVO.getMaterialPurpose());
                fillSegmentSummary(result, materials, leadGeneration);
                if (leadGeneration) {
                    addLeadGenerationSegmentErrors(result);
                } else {
                    addSegmentErrors(result, createReqVO, targetDuration);
                }
            }
        }
        result.setPassed(!result.hasErrors());
        return result;
    }

    private TkGenerationPrecheckRespVO.PrecheckIssue addError(TkGenerationPrecheckRespVO result, String code, String message) {
        return addError(result, code, message, message, "请按提示补齐配置后重新预检。");
    }

    private TkGenerationPrecheckRespVO.PrecheckIssue addError(TkGenerationPrecheckRespVO result, String code,
                                                              String message, String title, String actionHint) {
        TkGenerationPrecheckRespVO.PrecheckIssue issue = new TkGenerationPrecheckRespVO.PrecheckIssue(code, message);
        issue.setTitle(title);
        issue.setActionHint(actionHint);
        result.getErrors().add(issue);
        return issue;
    }

    private void fillPhaseSummary(TkGenerationPrecheckRespVO result, List<TkMaterialVideoDO> materials) {
        if (CollUtil.isEmpty(materials)) {
            return;
        }
        TkGenerationPrecheckRespVO.PhaseSummary summary = result.getPhaseSummary();
        for (TkMaterialVideoDO material : materials) {
            int duration = material.getDuration() == null || material.getDuration() <= 0 ? 0 : material.getDuration().intValue();
            TkMaterialUsagePhaseEnum phase = TkMaterialUsagePhaseEnum.normalize(material.getUsagePhase());
            if (phase == TkMaterialUsagePhaseEnum.ATTENTION) {
                summary.setAttentionCount(summary.getAttentionCount() + 1);
                summary.setAttentionDuration(summary.getAttentionDuration() + duration);
            } else if (phase == TkMaterialUsagePhaseEnum.PRODUCT_SHOW) {
                summary.setProductShowCount(summary.getProductShowCount() + 1);
                summary.setProductShowDuration(summary.getProductShowDuration() + duration);
            } else if (phase == TkMaterialUsagePhaseEnum.RESULT_EFFECT) {
                summary.setResultEffectCount(summary.getResultEffectCount() + 1);
                summary.setResultEffectDuration(summary.getResultEffectDuration() + duration);
            } else {
                summary.setGeneralCount(summary.getGeneralCount() + 1);
                summary.setGeneralDuration(summary.getGeneralDuration() + duration);
            }
        }
    }

    private void fillSegmentSummary(TkGenerationPrecheckRespVO result, List<TkMaterialVideoDO> materials,
                                    boolean leadGeneration) {
        List<TkMaterialSegmentTypeEnum> requiredSegments = leadGeneration
                ? TkMaterialSegmentTypeEnum.LEAD_GENERATION_SEGMENTS : TkMaterialSegmentTypeEnum.STORY_SEGMENTS;
        Map<TkMaterialSegmentTypeEnum, SegmentCounter> counters = new EnumMap<>(TkMaterialSegmentTypeEnum.class);
        for (TkMaterialSegmentTypeEnum segment : requiredSegments) {
            counters.put(segment, new SegmentCounter());
        }
        if (CollUtil.isNotEmpty(materials)) {
            for (TkMaterialVideoDO material : materials) {
                TkMaterialSegmentTypeEnum segment = resolveSegmentType(material);
                if (!counters.containsKey(segment)) {
                    continue;
                }
                SegmentCounter counter = counters.get(segment);
                counter.count++;
                counter.duration += material.getDuration() == null || material.getDuration() <= 0
                        ? 0 : material.getDuration().intValue();
            }
        }
        result.getSegmentSummary().clear();
        for (TkMaterialSegmentTypeEnum segment : requiredSegments) {
            SegmentCounter counter = counters.get(segment);
            result.getSegmentSummary().add(new TkGenerationPrecheckRespVO.SegmentSummaryItem(
                    segment.getCode(), segment.getName(), counter.count, counter.duration, isKeySegment(segment), 0, 0));
        }
    }

    private void addLeadGenerationSegmentErrors(TkGenerationPrecheckRespVO result) {
        for (TkGenerationPrecheckRespVO.SegmentSummaryItem item : result.getSegmentSummary()) {
            if (item.getCount() != null && item.getCount() > 0) {
                continue;
            }
            item.setRequiredDuration(0);
            item.setMissingDuration(0);
            TkGenerationPrecheckRespVO.PrecheckIssue issue = addError(result,
                    "SEGMENT_" + item.getSegmentType() + "_MISSING",
                    StrUtil.format("{}至少需要 1 个可用视频。", item.getSegmentName()),
                    StrUtil.format("缺少{}视频", item.getSegmentName()),
                    buildSegmentActionHint(item.getSegmentName()));
            issue.setSegmentType(item.getSegmentType());
            issue.setSegmentName(item.getSegmentName());
        }
    }

    private void addFullPoolRandomWarnings(TkGenerationPrecheckRespVO result, List<TkMaterialVideoDO> materials,
                                           int targetDuration) {
        if (CollUtil.isEmpty(materials)) {
            return;
        }
        int totalDuration = materials.stream()
                .map(TkMaterialVideoDO::getDuration)
                .filter(duration -> duration != null && duration > 0)
                .mapToInt(Long::intValue)
                .sum();
        if (totalDuration > 0 && totalDuration < targetDuration) {
            TkGenerationPrecheckRespVO.PrecheckIssue warning = new TkGenerationPrecheckRespVO.PrecheckIssue(
                    "MATERIAL_DURATION_SHORTER_THAN_TARGET",
                    StrUtil.format("全素材池随机混剪下，素材总时长 {} 秒短于目标 {} 秒，最终成片可能短于目标。", totalDuration, targetDuration));
            warning.setTitle("素材可完整使用，但总时长短于目标");
            warning.setActionHint("继续生成即可，系统会自动使用所有能完整拼接的素材。");
            warning.setRequiredDuration(targetDuration);
            warning.setActualDuration(totalDuration);
            warning.setMissingDuration(targetDuration - totalDuration);
            result.getWarnings().add(warning);
        }
    }

    private void addFullPoolRandomErrors(TkGenerationPrecheckRespVO result, List<TkMaterialVideoDO> materials,
                                         int targetDuration) {
        List<TkMaterialVideoDO> usableMaterials = materials.stream()
                .filter(item -> item.getDuration() != null && item.getDuration() > 0)
                .filter(item -> StrUtil.isNotBlank(item.getFileUrl()))
                .collect(java.util.stream.Collectors.toList());
        if (usableMaterials.isEmpty()) {
            addError(result, "MATERIAL_EMPTY", "素材库没有可用于随机混剪的可用视频。", "素材库没有可用视频",
                    "请先上传可用的视频素材。");
            return;
        }
        boolean hasFitClip = usableMaterials.stream()
                .anyMatch(item -> item.getDuration().intValue() <= targetDuration);
        if (!hasFitClip) {
            TkGenerationPrecheckRespVO.PrecheckIssue issue = addError(result, "MATERIAL_TOO_LONG_FOR_TARGET",
                    StrUtil.format("素材库里所有可用视频都长于目标 {} 秒，无法在不裁切的前提下生成成片。", targetDuration),
                    "所有可用素材都过长",
                    "请提高目标时长，或者补充更短的视频素材。");
            issue.setRequiredDuration(targetDuration);
            issue.setActualDuration(usableMaterials.stream()
                    .map(TkMaterialVideoDO::getDuration)
                    .filter(duration -> duration != null && duration > 0)
                    .mapToInt(Long::intValue)
                    .min()
                    .orElse(0));
        }
    }

    private void addSegmentErrors(TkGenerationPrecheckRespVO result, TkGenerationTaskCreateReqVO createReqVO, int targetDuration) {
        Map<TkMaterialSegmentTypeEnum, Integer> targets = resolveSegmentTargets(result, createReqVO, targetDuration);
        if (targets.isEmpty()) {
            return;
        }
        if (hasOpeningVideo(createReqVO)) {
            targets.put(TkMaterialSegmentTypeEnum.S1_HOOK, 0);
        }
        for (TkGenerationPrecheckRespVO.SegmentSummaryItem item : result.getSegmentSummary()) {
            TkMaterialSegmentTypeEnum segment = TkMaterialSegmentTypeEnum.normalize(item.getSegmentType());
            int requiredDuration = targets.getOrDefault(segment, 0);
            int actualDuration = item.getDuration() == null ? 0 : item.getDuration();
            int missingDuration = Math.max(0, requiredDuration - actualDuration);
            item.setRequiredDuration(requiredDuration);
            item.setMissingDuration(missingDuration);
            if (missingDuration > 0) {
                addSegmentError(result, item, requiredDuration, actualDuration, missingDuration);
            }
        }
    }

    private void addSegmentError(TkGenerationPrecheckRespVO result,
                                 TkGenerationPrecheckRespVO.SegmentSummaryItem item,
                                 int requiredDuration,
                                 int actualDuration,
                                 int missingDuration) {
        TkGenerationPrecheckRespVO.PrecheckIssue issue = addError(result, "SEGMENT_" + item.getSegmentType() + "_INSUFFICIENT",
                StrUtil.format("{} 用途素材不足，还缺 {} 秒，请上传或重新标记该素材用途。",
                        item.getSegmentName(), missingDuration),
                StrUtil.format("{}素材不足，还差 {} 秒", item.getSegmentName(), missingDuration),
                buildSegmentActionHint(item.getSegmentName()));
        issue.setSegmentType(item.getSegmentType());
        issue.setSegmentName(item.getSegmentName());
        issue.setRequiredDuration(requiredDuration);
        issue.setActualDuration(actualDuration);
        issue.setMissingDuration(missingDuration);
    }

    private String buildSegmentActionHint(String segmentName) {
        if ("效果证明".equals(segmentName)) {
            return "请上传效果展示、使用前后对比、用户反馈、成品展示类素材，或把已有合适素材标记为「效果证明」。";
        }
        return StrUtil.format("请上传{}类素材，或把已有合适素材标记为「{}」。", segmentName, segmentName);
    }

    private Map<TkMaterialSegmentTypeEnum, Integer> resolveSegmentTargets(TkGenerationPrecheckRespVO result,
                                                                          TkGenerationTaskCreateReqVO createReqVO,
                                                                          int targetDuration) {
        if (!TkSegmentDurationConfigSupport.hasConfig(createReqVO.getSegmentDurationConfig())) {
            return allocateSegmentTargets(targetDuration);
        }
        Map<TkMaterialSegmentTypeEnum, Integer> targets =
                TkSegmentDurationConfigSupport.parseTargets(createReqVO.getSegmentDurationConfig());
        int configuredDuration = TkSegmentDurationConfigSupport.totalDuration(targets);
        if (configuredDuration != targetDuration) {
            addError(result, "SEGMENT_DURATION_CONFIG_INVALID",
                    StrUtil.format("自定义用途秒数合计必须等于目标时长，当前 {} 秒，目标 {} 秒。",
                            configuredDuration, targetDuration),
                    "剪辑结构秒数不匹配", "请调整各用途秒数，让合计时长等于目标成片时长。");
            return new EnumMap<>(TkMaterialSegmentTypeEnum.class);
        }
        return targets;
    }

    private TkMaterialSegmentTypeEnum resolveSegmentType(TkMaterialVideoDO material) {
        return TkMaterialSegmentTypeEnum.normalize(material.getSegmentType());
    }

    private boolean isKeySegment(TkMaterialSegmentTypeEnum segment) {
        return segment == TkMaterialSegmentTypeEnum.S1_HOOK
                || segment == TkMaterialSegmentTypeEnum.S3_REVEAL
                || segment == TkMaterialSegmentTypeEnum.S4_DEMO
                || segment == TkMaterialSegmentTypeEnum.S5_PROOF;
    }

    private Map<TkMaterialSegmentTypeEnum, Integer> allocateSegmentTargets(int targetDuration) {
        if (targetDuration <= 15) {
            return buildSegmentTargets(Arrays.asList(TkMaterialSegmentTypeEnum.S1_HOOK, TkMaterialSegmentTypeEnum.S3_REVEAL,
                    TkMaterialSegmentTypeEnum.S4_DEMO, TkMaterialSegmentTypeEnum.S5_PROOF), targetDuration);
        }
        if (targetDuration <= 20) {
            return buildSegmentTargets(Arrays.asList(TkMaterialSegmentTypeEnum.S1_HOOK, TkMaterialSegmentTypeEnum.S2_PAIN,
                    TkMaterialSegmentTypeEnum.S3_REVEAL, TkMaterialSegmentTypeEnum.S4_DEMO,
                    TkMaterialSegmentTypeEnum.S5_PROOF), targetDuration);
        }
        if (targetDuration <= 30) {
            return buildSegmentTargets(Arrays.asList(TkMaterialSegmentTypeEnum.S1_HOOK, TkMaterialSegmentTypeEnum.S2_PAIN,
                    TkMaterialSegmentTypeEnum.S3_REVEAL, TkMaterialSegmentTypeEnum.S4_DEMO,
                    TkMaterialSegmentTypeEnum.S5_PROOF, TkMaterialSegmentTypeEnum.S7_LIFESTYLE), targetDuration);
        }
        return buildSegmentTargets(TkMaterialSegmentTypeEnum.STORY_SEGMENTS, targetDuration);
    }

    private Map<TkMaterialSegmentTypeEnum, Integer> buildSegmentTargets(List<TkMaterialSegmentTypeEnum> segments, int targetDuration) {
        Map<TkMaterialSegmentTypeEnum, Integer> targets = new EnumMap<>(TkMaterialSegmentTypeEnum.class);
        int totalWeight = segments.stream().mapToInt(this::segmentWeight).sum();
        int remaining = targetDuration;
        for (int i = 0; i < segments.size(); i++) {
            int duration = i == segments.size() - 1
                    ? remaining
                    : Math.max(1, Math.round(targetDuration * segmentWeight(segments.get(i)) / (float) totalWeight));
            remaining -= duration;
            targets.put(segments.get(i), duration);
        }
        return targets;
    }

    private int segmentWeight(TkMaterialSegmentTypeEnum segment) {
        if (segment == TkMaterialSegmentTypeEnum.S1_HOOK) {
            return 3;
        }
        if (segment == TkMaterialSegmentTypeEnum.S4_DEMO) {
            return 8;
        }
        if (segment == TkMaterialSegmentTypeEnum.S5_PROOF || segment == TkMaterialSegmentTypeEnum.S7_LIFESTYLE) {
            return 5;
        }
        return 4;
    }

    private boolean hasOpeningVideo(TkGenerationTaskCreateReqVO createReqVO) {
        return StrUtil.isNotBlank(createReqVO.getOpeningVideoUrl())
                || StrUtil.isNotBlank(createReqVO.getOpeningVideoName());
    }

    private String resolveRouteConfig(TkGenerationTaskCreateReqVO createReqVO) {
        if (createReqVO != null && StrUtil.isNotBlank(createReqVO.getClipPlanMode())) {
            return TkGenerationRouteConfigSupport.buildClipPlanModeConfig(createReqVO.getClipPlanMode());
        }
        return null;
    }

    private static class SegmentCounter {
        private int count;
        private int duration;
    }

}
