package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkOpenTiktokPublishDispatchPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @Test
    void selectsAgedPendingBeforeRecentAndExcludesRecoveryRows() {
        List<TkOpenTiktokPublishDetailDO> candidates = List.of(
                pending("recent", 2),
                pending("aged-2", 12),
                pending("aged-1", 20),
                pending("has-publish-id", 30).setPublishId("publish-id"),
                pending("recovery", 40).setTiktokStatus("RECOVERY_REQUIRED")
        );

        List<TkOpenTiktokPublishDetailDO> selected = TkOpenTiktokPublishDispatchPolicy.select(candidates, NOW, 3);

        assertEquals(List.of("aged-1", "aged-2", "recent"), ids(selected));
    }

    @Test
    void reservesOneRecentSlotForEveryThreeAgedTasks() {
        List<TkOpenTiktokPublishDetailDO> candidates = new ArrayList<>();
        IntStream.rangeClosed(1, 4).forEach(i -> candidates.add(pending("aged-" + i, 10 + i)));
        IntStream.rangeClosed(1, 4).forEach(i -> candidates.add(pending("recent-" + i, i)));

        List<TkOpenTiktokPublishDetailDO> selected = TkOpenTiktokPublishDispatchPolicy.select(candidates, NOW, 4);

        assertEquals(List.of("aged-4", "aged-3", "aged-2", "recent-4"), ids(selected));
    }

    @Test
    void starvationThresholdPromotesOldestTasksToStrictPriority() {
        List<TkOpenTiktokPublishDetailDO> candidates = List.of(
                pending("recent", 1),
                pending("aged", 12),
                pending("starving", 16)
        );

        List<TkOpenTiktokPublishDetailDO> selected = TkOpenTiktokPublishDispatchPolicy.select(candidates, NOW, 2);

        assertEquals(List.of("starving", "aged"), ids(selected));
    }

    private static TkOpenTiktokPublishDetailDO pending(String detailId, long ageMinutes) {
        return new TkOpenTiktokPublishDetailDO()
                .setDetailId(detailId)
                .setStatus("PENDING")
                .setCreateTime(NOW.minusMinutes(ageMinutes));
    }

    private static List<String> ids(List<TkOpenTiktokPublishDetailDO> details) {
        return details.stream().map(TkOpenTiktokPublishDetailDO::getDetailId).collect(Collectors.toList());
    }
}
