package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Selects pending initialization work without allowing recent work to starve. */
public final class TkOpenTiktokPublishDispatchPolicy {
    static final Duration AGED_AFTER = Duration.ofMinutes(10);
    static final Duration STARVATION_AFTER = Duration.ofMinutes(15);
    static final int AGED_QUOTA = 3;
    static final int RECENT_QUOTA = 1;

    private TkOpenTiktokPublishDispatchPolicy() { }

    public static List<TkOpenTiktokPublishDetailDO> select(
            List<TkOpenTiktokPublishDetailDO> candidates, LocalDateTime now, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) return new ArrayList<>();
        LocalDateTime reference = Objects.requireNonNull(now, "now");
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        Comparator<TkOpenTiktokPublishDetailDO> oldestFirst = Comparator
                .comparing(TkOpenTiktokPublishDetailDO::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TkOpenTiktokPublishDetailDO::getId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TkOpenTiktokPublishDetailDO::getDetailId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        List<TkOpenTiktokPublishDetailDO> eligible = candidates.stream()
                .filter(detail -> "PENDING".equals(detail.getStatus()))
                .filter(detail -> detail.getPublishId() == null || detail.getPublishId().trim().isEmpty())
                .filter(detail -> !"RECOVERY_REQUIRED".equals(detail.getTiktokStatus()))
                .sorted(oldestFirst)
                .collect(Collectors.toList());
        if (eligible.isEmpty()) return new ArrayList<>();

        boolean hasStarving = eligible.stream()
                .anyMatch(detail -> age(detail, reference).compareTo(STARVATION_AFTER) >= 0);
        if (hasStarving) return new ArrayList<>(eligible.subList(0, Math.min(boundedLimit, eligible.size())));

        List<TkOpenTiktokPublishDetailDO> aged = eligible.stream()
                .filter(detail -> age(detail, reference).compareTo(AGED_AFTER) >= 0)
                .collect(Collectors.toList());
        List<TkOpenTiktokPublishDetailDO> recent = eligible.stream()
                .filter(detail -> age(detail, reference).compareTo(AGED_AFTER) < 0)
                .collect(Collectors.toList());
        List<TkOpenTiktokPublishDetailDO> selected = new ArrayList<>(Math.min(boundedLimit, eligible.size()));
        int agedIndex = 0;
        int recentIndex = 0;
        while (selected.size() < boundedLimit && (agedIndex < aged.size() || recentIndex < recent.size())) {
            for (int i = 0; i < AGED_QUOTA && selected.size() < boundedLimit && agedIndex < aged.size(); i++) {
                selected.add(aged.get(agedIndex++));
            }
            for (int i = 0; i < RECENT_QUOTA && selected.size() < boundedLimit && recentIndex < recent.size(); i++) {
                selected.add(recent.get(recentIndex++));
            }
            if (agedIndex >= aged.size() && recentIndex < recent.size()) {
                while (selected.size() < boundedLimit && recentIndex < recent.size()) selected.add(recent.get(recentIndex++));
            }
            if (recentIndex >= recent.size() && agedIndex < aged.size()) {
                while (selected.size() < boundedLimit && agedIndex < aged.size()) selected.add(aged.get(agedIndex++));
            }
        }
        return selected;
    }

    private static Duration age(TkOpenTiktokPublishDetailDO detail, LocalDateTime now) {
        LocalDateTime created = detail.getCreateTime();
        return created == null || created.isAfter(now) ? Duration.ZERO : Duration.between(created, now);
    }
}
