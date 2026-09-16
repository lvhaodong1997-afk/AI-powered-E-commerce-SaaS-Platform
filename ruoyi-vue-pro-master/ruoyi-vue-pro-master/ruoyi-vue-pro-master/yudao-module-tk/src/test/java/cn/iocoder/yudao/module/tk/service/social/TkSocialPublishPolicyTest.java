package cn.iocoder.yudao.module.tk.service.social;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialPublishPolicyTest {
    private String aggregate(String... statuses) throws Exception {
        return TkSocialPublishPolicy.aggregate(Arrays.asList(statuses));
    }

    @Test void waitsForAllTargetsBeforePartialSuccess() throws Exception {
        assertEquals("PROCESSING", aggregate("SUCCESS", "PENDING"));
        assertEquals("PARTIAL_SUCCESS", aggregate("SUCCESS", "FAILED"));
    }
    @Test void preservesUnknownAndReauthInsteadOfPretendingFailed() throws Exception {
        assertEquals("UNKNOWN", aggregate("UNKNOWN", "FAILED"));
        assertEquals("REAUTH_REQUIRED", aggregate("REAUTH_REQUIRED", "FAILED"));
        assertEquals("SUCCESS", aggregate("SUCCESS", "SUCCESS"));
    }
    @Test void neverAllowsRetryOfSuccessOrUnknown() throws Exception {
        for (String status : Arrays.asList("SUCCESS", "UNKNOWN", "PROCESSING", "PENDING")) {
            assertFalse(TkSocialPublishPolicy.canRetry(status));
        }
        assertTrue(TkSocialPublishPolicy.canRetry("FAILED"));
        assertTrue(TkSocialPublishPolicy.canRetry("REAUTH_REQUIRED"));
    }
}
