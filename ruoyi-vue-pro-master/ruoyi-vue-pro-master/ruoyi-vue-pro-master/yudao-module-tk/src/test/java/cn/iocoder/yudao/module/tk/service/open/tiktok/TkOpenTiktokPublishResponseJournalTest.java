package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.*;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkOpenTiktokPublishResponseJournalTest {
    @TempDir Path dir;
    @Test void retainsResponseAcrossRestartUntilDatabaseAcceptsIt() throws Exception {
        TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        java.util.Map<String,String> ciphertext = new java.util.HashMap<>();
        when(cipher.encrypt(anyString())).thenAnswer(call -> { ciphertext.put("encrypted", call.getArgument(0)); return "encrypted"; });
        when(cipher.decrypt("encrypted")).thenAnswer(call -> ciphertext.get("encrypted"));
        TkOpenTiktokPublishResponseJournal journal = new TkOpenTiktokPublishResponseJournal(dir.toString(), cipher);
        TkOpenTiktokPublishDetailDO detail = TkOpenTiktokPublishDetailDO.builder().id(1L).clientId("c")
                .taskId("t").detailId("detail_1").retryCount(0).status("PROCESSING").build();
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder().id(1L).detailId("detail_1")
                .attemptNo(0).ownerToken("owner").phase("INIT_SENT").build();
        journal.save(detail, attempt, "official-id", "encrypted-url");
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            assertEquals("encrypted", Files.readString(files.findFirst().orElseThrow()));
        }
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishAttemptService attempts = mock(TkOpenTiktokPublishAttemptService.class);
        when(details.selectByClientAndDetailId("c", "detail_1")).thenReturn(detail);
        when(attempts.current(detail)).thenReturn(attempt);
        doThrow(new IllegalStateException("database down")).doNothing().when(attempts)
                .saveResponse(eq(detail), any(), eq("official-id"), eq("encrypted-url"));
        TkOpenTiktokPublishResponseJournal restarted = new TkOpenTiktokPublishResponseJournal(dir.toString(), cipher);
        assertEquals(0, restarted.replay(10, details, attempts));
        assertEquals(1, restarted.replay(10, details, attempts));
        assertEquals(0, restarted.replay(10, details, attempts));
        verify(attempts, times(2)).saveResponse(eq(detail), any(), eq("official-id"), eq("encrypted-url"));
    }
}
