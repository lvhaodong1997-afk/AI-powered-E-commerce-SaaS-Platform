package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationRouteConfigSupport;
import cn.iocoder.yudao.module.tk.service.generation.TkSegmentDurationConfigSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DefaultTkClipPlannerService implements TkClipPlannerService {

    private static final int OPENING_SECONDS = 3;
    private static final int RECENT_SUCCESS_TASK_LIMIT = 10;
    private static final List<TkMaterialSegmentTypeEnum> LEAD_BACKFILL_PRIORITY = Arrays.asList(
            TkMaterialSegmentTypeEnum.GENERAL,
            TkMaterialSegmentTypeEnum.S7_LIFESTYLE,
            TkMaterialSegmentTypeEnum.S6_DETAIL,
            TkMaterialSegmentTypeEnum.S4_DEMO,
            TkMaterialSegmentTypeEnum.S5_PROOF,
            TkMaterialSegmentTypeEnum.S3_REVEAL,
            TkMaterialSegmentTypeEnum.S2_PAIN,
            TkMaterialSegmentTypeEnum.S1_HOOK
    );

    @Resource
    private TkMaterialVideoMapper materialVideoMapper;
    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private TkGenerationProperties generationProperties;
    private Random random = new Random();

    @Override
    public List<TkClipPlanItem> plan(TkGenerationTaskDO task, String scriptText) {
        return plan(task, scriptText, null);
    }

    @Override
    public List<TkClipPlanItem> plan(TkGenerationTaskDO task, String scriptText, Integer effectiveTargetDuration) {
        int requestedTargetDuration = TkVideoDurationSupport.normalize(task.getTargetDuration());
        int targetDuration = resolveEffectiveTargetDuration(requestedTargetDuration, effectiveTargetDuration);
        boolean nativeOpening = hasNativeOpening(task);
        List<TkClipPlanItem> plan;
        if (TkGenerationRouteConfigSupport.isFullPoolRandom(task.getGenerationRouteConfig())) {
            plan = planFullPoolRandom(task, targetDuration);
            return normalizePrecisePlan(task, plan, targetDuration);
        } else if (TkGeminiPromptConfig.isLeadGeneration(task.getMaterialPurpose())) {
            plan = planLeadGeneration(task, scriptText, targetDuration);
            return normalizePrecisePlan(task, plan, targetDuration);
        }
        plan = new ArrayList<>();
        int orderNo = 1;
        int bodyTargetDuration = nativeOpening
                ? resolveNativeBodyTargetDuration(task, targetDuration) : targetDuration;
        List<SegmentSection> sections = nativeOpening
                ? resolveNativeBodySections(task, requestedTargetDuration, bodyTargetDuration)
                : resolveSections(task, targetDuration);
        if (hasOpeningVideo(task)) {
            SegmentSection openingSection = sections.isEmpty()
                    ? new SegmentSection(TkMaterialSegmentTypeEnum.S1_HOOK, 0, 0, "", "")
                    : sections.get(0);
            if (nativeOpening) {
                plan.add(buildNativeOpeningPlanItem(orderNo++, task, targetDuration));
            } else {
                int openingDuration = openingDuration(task, openingSection.targetDuration, targetDuration);
                plan.add(new TkClipPlanItem(orderNo++, "OPENING", null, task.getOpeningVideoName(),
                        task.getOpeningVideoUrl(), 0, openingDuration,
                        StrUtil.format("开头视频完整使用，归入{}环节", openingSection.name()),
                        openingSection.code(), openingSection.name(), openingSection.order(), null,
                        openingSection.scriptLine, openingSection.visualDirection, openingSection.targetDuration));
            }
        }
        List<TkMaterialVideoDO> materials = materialVideoMapper.selectListByLibraryId(task.getLibraryId());
        if (CollUtil.isEmpty(materials)) {
            throw new IllegalStateException("素材库没有可用于混剪的视频");
        }
        Set<Long> recentlyUsedMaterialIds = resolveRecentlyUsedMaterialIds(task);
        Set<Long> selectedMaterialIds = new HashSet<>();

        for (SegmentSection section : sections) {
            int selectedDuration = plan.stream()
                    .filter(item -> section.code().equals(item.getSection()))
                    .mapToInt(TkClipPlanItem::getDurationSecond)
                    .sum();
            for (TkMaterialVideoDO material : prioritizedSegmentCandidates(section.segment, materials, recentlyUsedMaterialIds)) {
                if (selectedDuration >= section.targetDuration) {
                    break;
                }
                if (material.getId() != null && selectedMaterialIds.contains(material.getId())) {
                    continue;
                }
                int duration = materialDuration(material);
                if (duration <= 0) {
                    continue;
                }
                plan.add(new TkClipPlanItem(orderNo++, "MATERIAL", material.getId(), material.getFileName(),
                        material.getFileUrl(), 0, duration,
                        buildReason(section, material, scriptText, duration),
                        section.code(), section.name(), section.order(), null,
                        section.scriptLine, section.visualDirection, section.targetDuration));
                selectedMaterialIds.add(material.getId());
                selectedDuration += duration;
            }
            while (selectedDuration < section.targetDuration) {
                TkMaterialVideoDO material = selectClosestUnusedMaterial(
                        materials, selectedMaterialIds, section.targetDuration - selectedDuration);
                if (material == null) {
                    break;
                }
                int duration = materialDuration(material);
                plan.add(new TkClipPlanItem(orderNo++, "MATERIAL", material.getId(), material.getFileName(),
                        material.getFileUrl(), 0, duration,
                        StrUtil.format("{}素材不足，选择最接近缺口的未使用素材并在渲染时适配到目标时长", section.name()),
                        section.code(), section.name(), section.order(), null,
                        section.scriptLine, section.visualDirection, section.targetDuration));
                selectedMaterialIds.add(material.getId());
                selectedDuration += duration;
            }
        }
        int planTargetDuration = nativeOpening
                ? (int) Math.ceil(nativeOpeningDurationSeconds(task)) + bodyTargetDuration : targetDuration;
        if (planDuration(plan) < planTargetDuration) {
            backfillWholeMaterials(plan, nativeOpening
                            ? randomBackfillCandidatesWithoutHook(materials)
                            : randomBackfillCandidates(materials), planTargetDuration, orderNo,
                    "音频时长补足，完整追加{}秒素材，避免尾帧停留");
        }
        return normalizePrecisePlan(task, plan, targetDuration);
    }

    private int resolveEffectiveTargetDuration(int requestedTargetDuration, Integer effectiveTargetDuration) {
        if (effectiveTargetDuration == null) {
            return requestedTargetDuration;
        }
        int normalizedEffective = TkVideoDurationSupport.normalize(effectiveTargetDuration);
        return Math.max(requestedTargetDuration, normalizedEffective);
    }

    private List<TkClipPlanItem> planFullPoolRandom(TkGenerationTaskDO task, int targetDuration) {
        List<TkClipPlanItem> plan = new ArrayList<>();
        int orderNo = 1;
        int randomTargetDuration = targetDuration;
        boolean nativeOpening = hasNativeOpening(task);
        if (hasOpeningVideo(task)) {
            if (nativeOpening) {
                plan.add(buildNativeOpeningPlanItem(orderNo++, task, targetDuration));
                randomTargetDuration = resolveNativeBodyTargetDuration(task, targetDuration);
            } else {
            int fixedOpeningDuration = openingDuration(task, 0, targetDuration);
            TkMaterialSegmentTypeEnum openingSegment = TkMaterialSegmentTypeEnum.S1_HOOK;
            plan.add(new TkClipPlanItem(orderNo++, "OPENING", null, task.getOpeningVideoName(),
                    task.getOpeningVideoUrl(), 0, fixedOpeningDuration,
                    "固定黄金3秒片头，后续从全素材池随机拼接",
                    openingSegment.getCode(), openingSegment.getName(), 1, null));
            randomTargetDuration = Math.max(0, targetDuration - fixedOpeningDuration);
            }
        }
        if (randomTargetDuration <= 0) {
            return plan;
        }
        List<TkMaterialVideoDO> materials = materialVideoMapper.selectListByLibraryId(task.getLibraryId());
        if (CollUtil.isEmpty(materials)) {
            throw new IllegalStateException("素材库没有可用于随机混剪的可用视频");
        }
        List<TkMaterialVideoDO> candidates = materials.stream()
                .filter(this::isUsableRandomClip)
                .filter(material -> !nativeOpening || resolveSegmentType(material) != TkMaterialSegmentTypeEnum.S1_HOOK)
                .collect(Collectors.toCollection(ArrayList::new));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("素材库没有可用于随机混剪的可用视频");
        }
        List<TkMaterialVideoDO> selected = new ArrayList<>(selectBestFitRandomClips(candidates, randomTargetDuration));
        if (selected.isEmpty()) {
            selected.add(selectClosestDurationMaterial(candidates, randomTargetDuration));
        }
        for (TkMaterialVideoDO material : selected) {
            int duration = materialDuration(material);
            plan.add(new TkClipPlanItem(orderNo++, "MATERIAL", material.getId(), material.getFileName(),
                    material.getFileUrl(), 0, duration,
                    StrUtil.format("全素材池随机混剪，完整使用{}秒素材", duration),
                    null, null, null, null, null, null, randomTargetDuration));
        }
        int planTargetDuration = nativeOpening
                ? (int) Math.ceil(nativeOpeningDurationSeconds(task)) + randomTargetDuration : targetDuration;
        if (planDuration(plan) < planTargetDuration) {
            backfillWholeMaterials(plan, randomBackfillCandidates(candidates), planTargetDuration, orderNo,
                    "全素材池随机混剪，音频时长补足，完整追加{}秒素材");
        }
        return plan;
    }

    private List<TkMaterialVideoDO> selectBestFitRandomClips(List<TkMaterialVideoDO> candidates, int targetDuration) {
        if (targetDuration <= 0) {
            return Collections.emptyList();
        }
        List<TkMaterialVideoDO> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);

        int[] prev = new int[targetDuration + 1];
        int[] pickedIndex = new int[targetDuration + 1];
        Arrays.fill(prev, -1);
        Arrays.fill(pickedIndex, -1);
        prev[0] = -2;
        for (int i = 0; i < shuffled.size(); i++) {
            int duration = materialDuration(shuffled.get(i));
            if (duration <= 0 || duration > targetDuration) {
                continue;
            }
            for (int sum = targetDuration; sum >= duration; sum--) {
                if (prev[sum] != -1 || prev[sum - duration] == -1) {
                    continue;
                }
                prev[sum] = sum - duration;
                pickedIndex[sum] = i;
            }
        }

        int bestSum = 0;
        for (int sum = targetDuration; sum >= 1; sum--) {
            if (prev[sum] != -1) {
                bestSum = sum;
                break;
            }
        }
        if (bestSum <= 0) {
            return Collections.emptyList();
        }

        List<TkMaterialVideoDO> selected = new ArrayList<>();
        while (bestSum > 0) {
            int index = pickedIndex[bestSum];
            if (index < 0) {
                return Collections.emptyList();
            }
            TkMaterialVideoDO material = shuffled.get(index);
            selected.add(material);
            bestSum = prev[bestSum];
        }
        Collections.shuffle(selected, random);
        return selected;
    }

    private boolean isUsableRandomClip(TkMaterialVideoDO material) {
        return material != null
                && material.getId() != null
                && StrUtil.isNotBlank(material.getFileUrl())
                && materialDuration(material) > 0;
    }

    private List<TkClipPlanItem> planLeadGeneration(TkGenerationTaskDO task, String scriptText, int targetDuration) {
        List<TkMaterialVideoDO> materials = materialVideoMapper.selectListByLibraryId(task.getLibraryId());
        if (CollUtil.isEmpty(materials)) {
            throw new IllegalStateException("素材库没有可用于混剪的视频");
        }
        Set<Long> recentlyUsedMaterialIds = resolveRecentlyUsedMaterialIds(task);
        List<TkClipPlanItem> plan = new ArrayList<>();
        int orderNo = 1;
        boolean nativeOpening = hasNativeOpening(task);
        int bodyTargetDuration = nativeOpening
                ? resolveNativeBodyTargetDuration(task, targetDuration) : targetDuration;
        if (nativeOpening) {
            plan.add(buildNativeOpeningPlanItem(orderNo++, task, targetDuration));
        }
        Set<Long> selectedMaterialIds = new HashSet<>();
        for (TkMaterialSegmentTypeEnum segment : TkMaterialSegmentTypeEnum.LEAD_GENERATION_SEGMENTS) {
            if (nativeOpening && segment == TkMaterialSegmentTypeEnum.S1_HOOK) {
                continue;
            }
            TkMaterialVideoDO selected = prioritizedSectionCandidates(segment, materials, recentlyUsedMaterialIds).stream()
                    .filter(material -> materialDuration(material) > 0)
                    .filter(material -> material.getId() == null || !selectedMaterialIds.contains(material.getId()))
                    .findFirst().orElse(null);
            if (selected == null) {
                continue;
            }
            int duration = materialDuration(selected);
            plan.add(new TkClipPlanItem(orderNo, "MATERIAL", selected.getId(), selected.getFileName(),
                    selected.getFileUrl(), 0, duration,
                    StrUtil.format("引流素材按固定结构拼装，完整使用{} {} 秒素材", segment.getName(), duration),
                    segment.getCode(), segment.getName(), orderNo, null, null, null, null));
            if (selected.getId() != null) {
                selectedMaterialIds.add(selected.getId());
            }
            orderNo++;
        }
        int planTargetDuration = nativeOpening
                ? (int) Math.ceil(nativeOpeningDurationSeconds(task)) + bodyTargetDuration : targetDuration;
        backfillLeadGenerationPlan(plan, nativeOpening
                        ? materials.stream()
                        .filter(material -> resolveSegmentType(material) != TkMaterialSegmentTypeEnum.S1_HOOK)
                        .collect(Collectors.toList())
                        : materials,
                recentlyUsedMaterialIds, orderNo, planTargetDuration);
        return plan;
    }

    private void backfillLeadGenerationPlan(List<TkClipPlanItem> plan,
                                            List<TkMaterialVideoDO> materials, Set<Long> recentlyUsedMaterialIds,
                                            int orderNo, int targetDuration) {
        if (targetDuration <= 0) {
            return;
        }
        int selectedDuration = plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum();
        if (selectedDuration >= targetDuration) {
            return;
        }
        List<TkMaterialVideoDO> candidates = leadBackfillCandidates(materials, recentlyUsedMaterialIds);
        if (CollUtil.isEmpty(candidates)) {
            return;
        }
        Set<Long> selectedMaterialIds = plan.stream()
                .map(TkClipPlanItem::getMaterialVideoId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        List<TkMaterialVideoDO> selected = selectBestFitBackfillMaterials(candidates, selectedMaterialIds,
                targetDuration - selectedDuration);
        for (TkMaterialVideoDO material : selected) {
            int duration = materialDuration(material);
            TkMaterialSegmentTypeEnum segment = resolveSegmentType(material);
            int currentOrderNo = orderNo++;
            plan.add(new TkClipPlanItem(currentOrderNo, "MATERIAL", material.getId(), material.getFileName(),
                    material.getFileUrl(), 0, duration,
                    StrUtil.format("引流素材时长补足，完整追加{} {} 秒素材，避免口播被截断",
                            segment.getName(), duration),
                    segment.getCode(), segment.getName(), currentOrderNo, null, null, null, null));
            selectedDuration += duration;
        }
    }

    private void backfillWholeMaterials(List<TkClipPlanItem> plan, List<TkMaterialVideoDO> candidates,
                                        int targetDuration, int orderNo, String reasonTemplate) {
        if (targetDuration <= 0 || CollUtil.isEmpty(candidates)) {
            return;
        }
        int selectedDuration = plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum();
        if (selectedDuration >= targetDuration) {
            return;
        }
        Set<Long> selectedMaterialIds = plan.stream()
                .map(TkClipPlanItem::getMaterialVideoId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(HashSet::new));
        List<TkMaterialVideoDO> selected = selectBestFitBackfillMaterials(candidates, selectedMaterialIds,
                targetDuration - selectedDuration);
        for (TkMaterialVideoDO material : selected) {
            int duration = materialDuration(material);
            plan.add(new TkClipPlanItem(orderNo++, "MATERIAL", material.getId(), material.getFileName(),
                    material.getFileUrl(), 0, duration, StrUtil.format(reasonTemplate, duration)));
            selectedDuration += duration;
            if (material.getId() != null) {
                selectedMaterialIds.add(material.getId());
            }
        }
    }

    @Override
    public List<TkClipPlanItem> replanTailForLowDynamic(TkGenerationTaskDO task, List<TkClipPlanItem> originalPlan,
                                                        Integer effectiveTargetDuration) {
        if (task == null || CollUtil.isEmpty(originalPlan)
                || !TkGeminiPromptConfig.isLeadGeneration(task.getMaterialPurpose())) {
            return originalPlan;
        }
        int targetDuration = resolveEffectiveTargetDuration(
                TkVideoDurationSupport.normalize(task.getTargetDuration()), effectiveTargetDuration);
        List<TkMaterialVideoDO> materials = materialVideoMapper.selectListByLibraryId(task.getLibraryId());
        if (CollUtil.isEmpty(materials)) {
            return originalPlan;
        }
        Set<Long> recentlyUsedMaterialIds = resolveRecentlyUsedMaterialIds(task);
        List<TkMaterialVideoDO> candidates = leadBackfillCandidates(materials, recentlyUsedMaterialIds);
        int maxRemoveCount = Math.min(2, originalPlan.size());
        for (int removeCount = 1; removeCount <= maxRemoveCount; removeCount++) {
            List<TkClipPlanItem> fixedPlan = new ArrayList<>(originalPlan.subList(0, originalPlan.size() - removeCount));
            int fixedDuration = planDuration(fixedPlan);
            Set<Long> fixedMaterialIds = fixedPlan.stream()
                    .map(TkClipPlanItem::getMaterialVideoId)
                    .filter(id -> id != null)
                    .collect(Collectors.toCollection(HashSet::new));
            originalPlan.subList(originalPlan.size() - removeCount, originalPlan.size()).stream()
                    .map(TkClipPlanItem::getMaterialVideoId)
                    .filter(id -> id != null)
                    .forEach(fixedMaterialIds::add);
            List<TkMaterialVideoDO> selected = selectBestFitBackfillMaterials(candidates, fixedMaterialIds,
                    targetDuration - fixedDuration);
            if (selected.isEmpty()) {
                continue;
            }
            List<TkClipPlanItem> replanned = new ArrayList<>(fixedPlan);
            int orderNo = replanned.size() + 1;
            for (TkMaterialVideoDO material : selected) {
                int duration = materialDuration(material);
                TkMaterialSegmentTypeEnum segment = resolveSegmentType(material);
                replanned.add(new TkClipPlanItem(orderNo, "MATERIAL", material.getId(), material.getFileName(),
                        material.getFileUrl(), 0, duration,
                        StrUtil.format("尾部低动态重排，完整使用{} {} 秒素材", segment.getName(), duration),
                        segment.getCode(), segment.getName(), orderNo, null, null, null, null));
                orderNo++;
            }
            if (planDuration(replanned) >= targetDuration && !sameMaterialSequence(originalPlan, replanned)) {
                return replanned;
            }
        }
        return originalPlan;
    }

    private List<TkMaterialVideoDO> selectBestFitBackfillMaterials(List<TkMaterialVideoDO> candidates,
                                                                   Set<Long> selectedMaterialIds,
                                                                   int missingDuration) {
        if (missingDuration <= 0 || CollUtil.isEmpty(candidates)) {
            return Collections.emptyList();
        }
        List<TkMaterialVideoDO> usableCandidates = candidates.stream()
                .filter(this::isUsableRandomClip)
                .filter(material -> material.getId() == null || selectedMaterialIds == null
                        || !selectedMaterialIds.contains(material.getId()))
                .collect(Collectors.toList());
        BackfillChoice bestChoice = findBestFitBackfillChoice(usableCandidates, missingDuration);
        return bestChoice == null ? usableCandidates : bestChoice.materials;
    }

    private BackfillChoice findBestFitBackfillChoice(List<TkMaterialVideoDO> candidates, int missingDuration) {
        if (CollUtil.isEmpty(candidates)) {
            return null;
        }
        int maxCandidateCount = Math.min(candidates.size(), 20);
        int combinationCount = 1 << maxCandidateCount;
        BackfillChoice bestChoice = null;
        for (int mask = 1; mask < combinationCount; mask++) {
            List<TkMaterialVideoDO> selected = new ArrayList<>();
            int duration = 0;
            for (int index = 0; index < maxCandidateCount; index++) {
                if ((mask & (1 << index)) == 0) {
                    continue;
                }
                TkMaterialVideoDO material = candidates.get(index);
                selected.add(material);
                duration += materialDuration(material);
            }
            if (duration < missingDuration) {
                continue;
            }
            BackfillChoice choice = new BackfillChoice(selected, duration);
            if (bestChoice == null || choice.betterThan(bestChoice, missingDuration)) {
                bestChoice = choice;
            }
        }
        return bestChoice;
    }

    private boolean sameMaterialSequence(List<TkClipPlanItem> left, List<TkClipPlanItem> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!java.util.Objects.equals(left.get(i).getMaterialVideoId(), right.get(i).getMaterialVideoId())) {
                return false;
            }
        }
        return true;
    }

    private int planDuration(List<TkClipPlanItem> plan) {
        return plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum();
    }

    private List<TkMaterialVideoDO> randomBackfillCandidates(List<TkMaterialVideoDO> materials) {
        List<TkMaterialVideoDO> candidates = materials.stream()
                .filter(this::isUsableRandomClip)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(candidates, random);
        return candidates;
    }

    private List<TkMaterialVideoDO> randomBackfillCandidatesWithoutHook(List<TkMaterialVideoDO> materials) {
        return randomBackfillCandidates(materials.stream()
                .filter(material -> resolveSegmentType(material) != TkMaterialSegmentTypeEnum.S1_HOOK)
                .collect(Collectors.toList()));
    }

    private List<TkMaterialVideoDO> leadBackfillCandidates(List<TkMaterialVideoDO> materials,
                                                           Set<Long> recentlyUsedMaterialIds) {
        List<TkMaterialVideoDO> candidates = new ArrayList<>();
        Set<Long> addedIds = new HashSet<>();
        for (TkMaterialSegmentTypeEnum segment : LEAD_BACKFILL_PRIORITY) {
            for (TkMaterialVideoDO material : prioritizedSegmentCandidates(segment, materials, recentlyUsedMaterialIds)) {
                if (material.getId() != null && addedIds.contains(material.getId())) {
                    continue;
                }
                if (materialDuration(material) <= 0) {
                    continue;
                }
                candidates.add(material);
                if (material.getId() != null) {
                    addedIds.add(material.getId());
                }
            }
        }
        return candidates;
    }

    private Set<Long> resolveRecentlyUsedMaterialIds(TkGenerationTaskDO task) {
        if (taskMapper == null || task == null || task.getLibraryId() == null) {
            return Collections.emptySet();
        }
        List<TkGenerationTaskDO> recentTasks = taskMapper.selectRecentSuccessfulClipPlansByLibraryId(
                task.getLibraryId(), task.getId(), RECENT_SUCCESS_TASK_LIMIT);
        if (CollUtil.isEmpty(recentTasks)) {
            return Collections.emptySet();
        }
        Set<Long> materialIds = new HashSet<>();
        for (TkGenerationTaskDO recentTask : recentTasks) {
            collectClipPlanMaterialIds(recentTask.getClipPlan(), materialIds);
        }
        return materialIds;
    }

    private void collectClipPlanMaterialIds(String clipPlan, Set<Long> materialIds) {
        if (StrUtil.isBlank(clipPlan)) {
            return;
        }
        JsonNode root;
        try {
            root = JsonUtils.parseTree(clipPlan);
        } catch (RuntimeException ex) {
            return;
        }
        if (root == null || !root.isArray()) {
            return;
        }
        for (JsonNode item : root) {
            Long materialId = parseMaterialId(item.path("materialVideoId"));
            if (materialId != null) {
                materialIds.add(materialId);
            }
        }
    }

    private Long parseMaterialId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        String value = node.asText(null);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<SegmentSection> resolveSections(TkGenerationTaskDO task, int targetDuration) {
        List<SegmentSection> configuredSections = resolveConfiguredSections(task.getSegmentDurationConfig(), targetDuration);
        if (CollUtil.isNotEmpty(configuredSections)) {
            return configuredSections;
        }
        List<SegmentSection> routeSections = resolveConfiguredSections(task.getGenerationRouteConfig(), targetDuration);
        if (CollUtil.isNotEmpty(routeSections)) {
            return routeSections;
        }
        List<SegmentSection> timelineSections = parseSegmentTimeline(task.getSegmentTimeline());
        if (CollUtil.isNotEmpty(timelineSections)) {
            int totalDuration = timelineSections.stream().mapToInt(item -> item.targetDuration).sum();
            if (totalDuration == targetDuration) {
                return timelineSections;
            }
        }
        return allocateDefaultSections(targetDuration);
    }

    private List<SegmentSection> resolveNativeBodySections(TkGenerationTaskDO task, int requestedTargetDuration,
                                                            int bodyTargetDuration) {
        if (bodyTargetDuration <= 0) {
            return new ArrayList<>();
        }
        List<SegmentSection> sourceSections = resolveSections(task, requestedTargetDuration).stream()
                .filter(section -> section.segment != TkMaterialSegmentTypeEnum.S1_HOOK)
                .collect(Collectors.toList());
        if (sourceSections.isEmpty()) {
            return allocateBodyDefaultSections(bodyTargetDuration);
        }
        if (sourceSections.size() > bodyTargetDuration) {
            sourceSections = new ArrayList<>(sourceSections.subList(0, bodyTargetDuration));
        }
        int sourceTotal = sourceSections.stream().mapToInt(section -> section.targetDuration).sum();
        if (sourceTotal <= 0) {
            return allocateBodyDefaultSections(bodyTargetDuration);
        }
        int remaining = bodyTargetDuration;
        List<SegmentSection> result = new ArrayList<>();
        for (int index = 0; index < sourceSections.size(); index++) {
            SegmentSection source = sourceSections.get(index);
            int duration = index == sourceSections.size() - 1
                    ? remaining
                    : Math.max(1, Math.round(bodyTargetDuration * source.targetDuration / (float) sourceTotal));
            remaining -= duration;
            result.add(new SegmentSection(source.segment, duration, index + 1,
                    source.scriptLine, source.visualDirection));
        }
        return result;
    }

    private List<SegmentSection> allocateBodyDefaultSections(int targetDuration) {
        List<TkMaterialSegmentTypeEnum> segments = new ArrayList<>(TkMaterialSegmentTypeEnum.STORY_SEGMENTS);
        segments.remove(TkMaterialSegmentTypeEnum.S1_HOOK);
        return buildSections(segments, targetDuration);
    }

    private List<SegmentSection> resolveConfiguredSections(String segmentDurationConfig, int targetDuration) {
        Map<TkMaterialSegmentTypeEnum, Integer> targets = TkSegmentDurationConfigSupport.parseTargets(segmentDurationConfig);
        if (targets.isEmpty() || TkSegmentDurationConfigSupport.totalDuration(targets) != targetDuration) {
            return new ArrayList<>();
        }
        List<SegmentSection> sections = new ArrayList<>();
        for (TkMaterialSegmentTypeEnum segment : TkMaterialSegmentTypeEnum.STORY_SEGMENTS) {
            int duration = targets.getOrDefault(segment, 0);
            if (duration > 0) {
                sections.add(new SegmentSection(segment, duration, sections.size() + 1, "", ""));
            }
        }
        return sections;
    }

    private List<SegmentSection> parseSegmentTimeline(String segmentTimeline) {
        if (StrUtil.isBlank(segmentTimeline)) {
            return new ArrayList<>();
        }
        JsonNode root = JsonUtils.parseTree(segmentTimeline);
        if (root == null || !root.isArray()) {
            return new ArrayList<>();
        }
        List<SegmentSection> sections = new ArrayList<>();
        for (JsonNode item : root) {
            TkMaterialSegmentTypeEnum segment = TkMaterialSegmentTypeEnum.normalize(item.path("segmentLibrary").asText());
            if (!TkMaterialSegmentTypeEnum.STORY_SEGMENTS.contains(segment)) {
                continue;
            }
            int duration = parseWindowDuration(item.path("timeWindow").asText());
            if (duration <= 0) {
                continue;
            }
            sections.add(new SegmentSection(segment, duration, sections.size() + 1,
                    item.path("scriptLine").asText(null), item.path("visualDirection").asText(null)));
        }
        return sections;
    }

    private int parseWindowDuration(String timeWindow) {
        if (StrUtil.isBlank(timeWindow)) {
            return 0;
        }
        String normalized = timeWindow.replace("秒", "s").replace(" ", "");
        String[] parts = normalized.replace("s", "").split("-");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(parts[1]) - Integer.parseInt(parts[0]));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<SegmentSection> allocateDefaultSections(int targetDuration) {
        if (targetDuration <= 15) {
            return buildSections(Arrays.asList(TkMaterialSegmentTypeEnum.S1_HOOK, TkMaterialSegmentTypeEnum.S3_REVEAL,
                    TkMaterialSegmentTypeEnum.S4_DEMO, TkMaterialSegmentTypeEnum.S5_PROOF), targetDuration);
        }
        if (targetDuration <= 20) {
            return buildSections(Arrays.asList(TkMaterialSegmentTypeEnum.S1_HOOK, TkMaterialSegmentTypeEnum.S2_PAIN,
                    TkMaterialSegmentTypeEnum.S3_REVEAL, TkMaterialSegmentTypeEnum.S4_DEMO,
                    TkMaterialSegmentTypeEnum.S5_PROOF), targetDuration);
        }
        if (targetDuration <= 30) {
            return buildSections(Arrays.asList(TkMaterialSegmentTypeEnum.S1_HOOK, TkMaterialSegmentTypeEnum.S2_PAIN,
                    TkMaterialSegmentTypeEnum.S3_REVEAL, TkMaterialSegmentTypeEnum.S4_DEMO,
                    TkMaterialSegmentTypeEnum.S5_PROOF, TkMaterialSegmentTypeEnum.S7_LIFESTYLE), targetDuration);
        }
        return buildSections(TkMaterialSegmentTypeEnum.STORY_SEGMENTS, targetDuration);
    }

    private List<SegmentSection> buildSections(List<TkMaterialSegmentTypeEnum> segments, int targetDuration) {
        int[] weights = segments.stream().mapToInt(this::segmentWeight).toArray();
        int totalWeight = Arrays.stream(weights).sum();
        int remaining = targetDuration;
        List<SegmentSection> sections = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            int duration = i == segments.size() - 1
                    ? remaining
                    : Math.max(1, Math.round(targetDuration * weights[i] / (float) totalWeight));
            remaining -= duration;
            sections.add(new SegmentSection(segments.get(i), duration, i + 1, "", ""));
        }
        return sections;
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

    private List<TkMaterialVideoDO> prioritizedSegmentCandidates(TkMaterialSegmentTypeEnum segment,
                                                                 List<TkMaterialVideoDO> materials,
                                                                 Set<Long> recentlyUsedMaterialIds) {
        List<TkMaterialVideoDO> candidates = segmentCandidates(segment, materials);
        if (CollUtil.isEmpty(recentlyUsedMaterialIds)) {
            Collections.shuffle(candidates, random);
            return candidates;
        }
        List<TkMaterialVideoDO> freshCandidates = new ArrayList<>();
        List<TkMaterialVideoDO> fallbackCandidates = new ArrayList<>();
        for (TkMaterialVideoDO candidate : candidates) {
            if (candidate.getId() != null && recentlyUsedMaterialIds.contains(candidate.getId())) {
                fallbackCandidates.add(candidate);
            } else {
                freshCandidates.add(candidate);
            }
        }
        Collections.shuffle(freshCandidates, random);
        Collections.shuffle(fallbackCandidates, random);
        freshCandidates.addAll(fallbackCandidates);
        return freshCandidates;
    }

    private List<TkMaterialVideoDO> prioritizedSectionCandidates(TkMaterialSegmentTypeEnum segment,
                                                                  List<TkMaterialVideoDO> materials,
                                                                  Set<Long> recentlyUsedMaterialIds) {
        List<TkMaterialVideoDO> candidates = new ArrayList<>(
                prioritizedSegmentCandidates(segment, materials, recentlyUsedMaterialIds));
        Set<Long> addedIds = candidates.stream().map(TkMaterialVideoDO::getId)
                .filter(id -> id != null).collect(Collectors.toSet());
        for (TkMaterialVideoDO material : materials) {
            if (!isUsableRandomClip(material) || resolveSegmentType(material) == segment
                    || (material.getId() != null && addedIds.contains(material.getId()))) {
                continue;
            }
            candidates.add(material);
            if (material.getId() != null) {
                addedIds.add(material.getId());
            }
        }
        return candidates;
    }

    private TkMaterialVideoDO selectClosestDurationMaterial(List<TkMaterialVideoDO> candidates, int targetDuration) {
        return candidates.stream().min((left, right) -> Integer.compare(
                        Math.abs(materialDuration(left) - targetDuration), Math.abs(materialDuration(right) - targetDuration)))
                .orElseThrow(() -> new IllegalStateException("素材库没有可用于随机混剪的可用视频"));
    }

    private TkMaterialVideoDO selectClosestUnusedMaterial(List<TkMaterialVideoDO> materials,
                                                           Set<Long> selectedMaterialIds,
                                                           int missingDuration) {
        return materials.stream()
                .filter(this::isUsableRandomClip)
                .filter(material -> !selectedMaterialIds.contains(material.getId()))
                .min((left, right) -> Integer.compare(
                        Math.abs(materialDuration(left) - missingDuration),
                        Math.abs(materialDuration(right) - missingDuration)))
                .orElse(null);
    }

    private List<TkMaterialVideoDO> segmentCandidates(TkMaterialSegmentTypeEnum segment, List<TkMaterialVideoDO> materials) {
        return materials.stream()
                .filter(material -> resolveSegmentType(material) == segment)
                .collect(Collectors.toList());
    }

    private TkMaterialSegmentTypeEnum resolveSegmentType(TkMaterialVideoDO material) {
        return TkMaterialSegmentTypeEnum.normalize(material.getSegmentType());
    }

    private int remainingSecond(TkMaterialVideoDO material, int startSecond) {
        if (material.getDuration() == null || material.getDuration() <= 0) {
            return 0;
        }
        long remaining = material.getDuration() - startSecond;
        return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(remaining, 0);
    }

    private List<TkClipPlanItem> normalizePrecisePlan(TkGenerationTaskDO task, List<TkClipPlanItem> originalPlan,
                                                       int targetDuration) {
        if (CollUtil.isEmpty(originalPlan)) {
            return originalPlan;
        }
        List<TkMaterialVideoDO> materials = materialVideoMapper.selectListByLibraryId(task.getLibraryId());
        if (materials.stream().noneMatch(material -> material.getDurationMs() != null && material.getDurationMs() > 0L)) {
            // 历史素材尚未回填真实时长时保持旧计划，由渲染阶段探测并适配片段时长。
            return originalPlan;
        }
        Map<Long, TkMaterialVideoDO> materialMap = materials.stream()
                .filter(material -> material.getId() != null)
                .collect(Collectors.toMap(TkMaterialVideoDO::getId, material -> material, (left, right) -> left));
        for (TkClipPlanItem item : originalPlan) {
            TkMaterialVideoDO material = item.getMaterialVideoId() == null ? null : materialMap.get(item.getMaterialVideoId());
            if (material == null || material.getDurationMs() == null || material.getDurationMs() <= 0L) {
                continue;
            }
            long startMillis = item.getStartSecond() == null ? 0L : Math.max(0L, item.getStartSecond()) * 1000L;
            long durationMillis = Math.max(0L, material.getDurationMs() - startMillis);
            if (durationMillis > 0L) {
                item.setStartMillis(startMillis);
                item.setDurationMillis(durationMillis);
                item.setReuseMode("ORIGINAL");
            }
        }
        return originalPlan;
    }

    private long materialDurationMillis(TkMaterialVideoDO material) {
        if (material == null) {
            return 0L;
        }
        if (material.getDurationMs() != null && material.getDurationMs() > 0L) {
            return material.getDurationMs();
        }
        return material.getDuration() == null ? 0L : Math.max(0L, material.getDuration()) * 1000L;
    }

    private int ceilSeconds(long durationMillis) {
        return (int) Math.max(1L, (durationMillis + 999L) / 1000L);
    }

    private int materialDuration(TkMaterialVideoDO material) {
        return ceilSeconds(materialDurationMillis(material));
    }

    private int openingDuration(TkGenerationTaskDO task, int sectionTargetDuration, int targetDuration) {
        if (sectionTargetDuration > 0) {
            return sectionTargetDuration;
        }
        return Math.min(OPENING_SECONDS, targetDuration);
    }

    private boolean hasOpeningVideo(TkGenerationTaskDO task) {
        return task != null && StrUtil.isNotBlank(task.getOpeningVideoUrl());
    }

    private boolean hasNativeOpening(TkGenerationTaskDO task) {
        return hasOpeningVideo(task) && TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode());
    }

    private double nativeOpeningDurationSeconds(TkGenerationTaskDO task) {
        if (!hasOpeningVideo(task)) {
            return 0D;
        }
        if (task.getOpeningDurationMs() != null && task.getOpeningDurationMs() > 0L) {
            return task.getOpeningDurationMs() / 1000D;
        }
        throw new IllegalStateException("无法识别黄金开头视频时长，请检查视频文件或链接后重试");
    }

    private int resolveNativeBodyTargetDuration(TkGenerationTaskDO task, int targetDuration) {
        return Math.max(0, (int) Math.ceil(targetDuration - nativeOpeningDurationSeconds(task)));
    }

    private TkClipPlanItem buildNativeOpeningPlanItem(int orderNo, TkGenerationTaskDO task, int targetDuration) {
        long durationMillis = Math.max(1L, Math.round(nativeOpeningDurationSeconds(task) * 1000D));
        TkClipPlanItem item = new TkClipPlanItem(orderNo, "OPENING", null, task.getOpeningVideoName(),
                task.getOpeningVideoUrl(), 0, ceilSeconds(durationMillis),
                "原生保留黄金开头，后续配音和系统字幕从开头之后开始",
                TkMaterialSegmentTypeEnum.S1_HOOK.getCode(), TkMaterialSegmentTypeEnum.S1_HOOK.getName(), 1, null);
        item.setStartMillis(0L);
        item.setDurationMillis(durationMillis);
        item.setReuseMode("NATIVE");
        return item;
    }

    private String buildReason(SegmentSection section, TkMaterialVideoDO material, String scriptText, int duration) {
        return StrUtil.format("{}，从对应素材池随机抽取，完整使用 {} 秒素材", section.name(), duration);
    }

    private static class SegmentSection {

        private final TkMaterialSegmentTypeEnum segment;
        private final int targetDuration;
        private final int sectionOrder;
        private final String scriptLine;
        private final String visualDirection;

        private SegmentSection(TkMaterialSegmentTypeEnum segment, int targetDuration, int sectionOrder,
                               String scriptLine, String visualDirection) {
            this.segment = segment;
            this.targetDuration = targetDuration;
            this.sectionOrder = sectionOrder;
            this.scriptLine = scriptLine;
            this.visualDirection = visualDirection;
        }

        private String code() {
            return segment.getCode();
        }

        private String name() {
            return segment.getName();
        }

        private Integer order() {
            return sectionOrder;
        }

    }

    private static class BackfillChoice {

        private final List<TkMaterialVideoDO> materials;
        private final int duration;

        private BackfillChoice(List<TkMaterialVideoDO> materials, int duration) {
            this.materials = materials;
            this.duration = duration;
        }

        private boolean betterThan(BackfillChoice other, int missingDuration) {
            int overshoot = duration - missingDuration;
            int otherOvershoot = other.duration - missingDuration;
            if (overshoot != otherOvershoot) {
                return overshoot < otherOvershoot;
            }
            if (materials.size() != other.materials.size()) {
                return materials.size() < other.materials.size();
            }
            return lastDuration() < other.lastDuration();
        }

        private int lastDuration() {
            if (materials.isEmpty()) {
                return 0;
            }
            TkMaterialVideoDO last = materials.get(materials.size() - 1);
            return last.getDuration() == null ? 0 : last.getDuration().intValue();
        }

    }

}
