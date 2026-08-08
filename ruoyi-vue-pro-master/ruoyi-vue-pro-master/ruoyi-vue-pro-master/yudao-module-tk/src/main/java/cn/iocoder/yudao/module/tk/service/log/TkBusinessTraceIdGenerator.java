package cn.iocoder.yudao.module.tk.service.log;

import cn.hutool.core.util.IdUtil;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class TkBusinessTraceIdGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private TkBusinessTraceIdGenerator() {
    }

    public static String generate(Long tenantId) {
        String tenantPart = tenantId == null ? "0" : tenantId.toString();
        return "TK-" + LocalDate.now().format(DATE_FORMATTER) + "-" + tenantPart + "-" + IdUtil.getSnowflakeNextId();
    }

}
