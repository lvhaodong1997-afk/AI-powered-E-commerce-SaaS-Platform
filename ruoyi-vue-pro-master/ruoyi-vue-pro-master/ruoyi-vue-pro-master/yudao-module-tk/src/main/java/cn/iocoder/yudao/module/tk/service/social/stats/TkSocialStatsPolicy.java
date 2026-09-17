package cn.iocoder.yudao.module.tk.service.social.stats;

import org.springframework.beans.BeanUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class TkSocialStatsPolicy {
    private TkSocialStatsPolicy() { }
    public static List<TkSocialStatsMetric> merge(List<TkSocialStatsMetric> previous,List<TkSocialStatsMetric> incoming) {
        Map<String,TkSocialStatsMetric> merged=new LinkedHashMap<>();
        for(TkSocialStatsMetric m:previous) merged.put(m.getKey(),m);
        for(TkSocialStatsMetric value:incoming) {
            TkSocialStatsMetric copy=new TkSocialStatsMetric(); BeanUtils.copyProperties(value,copy);
            TkSocialStatsMetric old=merged.get(value.getKey());
            if (!"AVAILABLE".equals(value.getAvailability()) && old!=null && old.getValue()!=null) {
                copy.setValue(old.getValue()); copy.setFetchedAt(old.getFetchedAt());
                copy.setSourceMetric(old.getSourceMetric()); copy.setUnit(old.getUnit());
                copy.setScope(old.getScope()); copy.setPeriod(old.getPeriod());
            }
            merged.put(copy.getKey(),copy);
        }
        return new ArrayList<>(merged.values());
    }
    public static LocalDateTime nextMedia(LocalDateTime published,LocalDateTime now) {
        if (published==null) return null;
        Duration age=Duration.between(published,now);
        if (age.compareTo(Duration.ofDays(1))<0) return now.plusMinutes(30);
        if (age.compareTo(Duration.ofDays(7))<0) return now.plusHours(6);
        if (age.compareTo(Duration.ofDays(30))<0) return now.plusDays(1);
        return null;
    }
    public static LocalDateTime retryTime(LocalDateTime now,int retry,boolean transientFailure) {
        if (!transientFailure) return now.plusHours(6);
        long base=Math.min(3600L,60L << Math.min(6,Math.max(0,retry-1)));
        return now.plusSeconds(base+ThreadLocalRandom.current().nextLong(Math.max(1,base/4)));
    }
}
