package cn.iocoder.yudao.module.tk.service.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkBusinessTraceIdGeneratorTest {

    @Test
    void generateIncludesPrefixDateTenantAndNumericSequence() {
        String traceId = TkBusinessTraceIdGenerator.generate(8L);

        assertTrue(traceId.matches("TK-\\d{8}-8-\\d+"), traceId);
    }

}
