package cn.iocoder.yudao.module.tk.service.open.meta;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static cn.iocoder.yudao.module.tk.service.open.meta.TkOpenMetaAuth.result;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenMetaPublishTest {
    static final String WORKSPACE = "01234567-89ab-cdef-0123-456789abcdef";
    static final String MEDIA = "metamedia_0123456789abcdef0123456789abcdef";
    @TempDir Path root;

    @Test
    void requiresExactWorkspaceAndSignedIdempotency() {
        assertEquals(WORKSPACE, TkOpenMetaAccess.workspace("workspaceId=" + WORKSPACE));
        assertThrows(TkOpenApiException.class, () -> TkOpenMetaAccess.workspace("workspaceId=" + WORKSPACE + "&workspaceId=" + WORKSPACE));
        assertDoesNotThrow(() -> TkOpenMetaPublish.validate("facebook", null, "TEXT", "Caption", "key", "key"));
        assertThrows(TkOpenApiException.class, () -> TkOpenMetaPublish.validate("instagram", null, "TEXT", "Caption", "key", null));
        assertThrows(TkOpenApiException.class, () -> TkOpenMetaPublish.validate("facebook", MEDIA, "VIDEO", "", "key", "other"));
    }

    @Test
    void replaysOnlyTheIdenticalRequest() {
        Map<String, Object> row = result("id", "task", "request_hash", "hash");
        assertEquals("task", TkOpenMetaPublish.replay(row, "hash").get("taskId"));
        assertEquals(409, assertThrows(TkOpenApiException.class, () -> TkOpenMetaPublish.replay(row, "different")).getHttpStatus());
    }

    @Test
    void rejectsExpiredMediaAndDoesNotRetryAmbiguousMutations() {
        assertEquals(MEDIA, TkOpenMetaPublish.verifyTicket(MEDIA + ":101", 100));
        assertThrows(IllegalArgumentException.class, () -> TkOpenMetaPublish.verifyTicket(MEDIA + ":100", 100));
        assertEquals("UNKNOWN", TkOpenMetaPublish.failureStatus(new TkSocialPlatformException("TIMEOUT", "", true, false, true), false));
        assertEquals("UNKNOWN", TkOpenMetaPublish.failureStatus(new TkSocialPlatformException("DENIED", "", false, true, false), true));
        assertEquals("PENDING", TkOpenMetaPublish.failureStatus(new TkSocialPlatformException("RATE", "", true, false, false), false));
    }

    @Test
    void chunkReplayAndAssemblyAreScopedAndVerifyOriginalBytes() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        byte[] jpeg = out.toByteArray();
        StringBuilder sha = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(jpeg)) sha.append(String.format("%02x", b));
        JdbcTemplate db = mock(JdbcTemplate.class);
        TkOpenMetaAccess access = mock(TkOpenMetaAccess.class);
        TkOpenMetaAccess.Scope scope = new TkOpenMetaAccess.Scope("client", WORKSPACE, "facebook");
        when(access.require("facebook", "publish")).thenReturn(scope);
        when(access.privateRoot()).thenReturn(root);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenAnswer(i -> new SimpleTransactionStatus());
        TkOpenMetaMedia service = new TkOpenMetaMedia(db, new TransactionTemplate(manager), access);
        Map<String, Object> row = result("id", MEDIA, "status", "UPLOADING", "expires_at", LocalDateTime.now().plusHours(1),
                "file_size", jpeg.length, "sha256", sha.toString(), "content_type", "image/jpeg");
        when(db.queryForList(contains("client_id=? AND workspace_id=? AND platform=?"), eq(MEDIA), eq("client"),
                eq(WORKSPACE), eq("facebook"))).thenReturn(Collections.singletonList(row));
        when(db.update("UPDATE tk_open_meta_media SET status='READY' WHERE id=?", MEDIA)).thenAnswer(i -> { row.put("status", "READY"); return 1; });
        assertThrows(TkOpenApiException.class, () -> service.complete("facebook", MEDIA));
        service.chunk("facebook", MEDIA, 0, jpeg);
        service.chunk("facebook", MEDIA, 0, jpeg);
        byte[] different = jpeg.clone(); different[0] ^= 1;
        assertEquals(409, assertThrows(TkOpenApiException.class, () -> service.chunk("facebook", MEDIA, 0, different)).getHttpStatus());
        assertEquals(MEDIA, service.complete("facebook", MEDIA).get("mediaId"));
        assertArrayEquals(jpeg, Files.readAllBytes(service.file(MEDIA)));
        when(access.require("facebook", "publish")).thenReturn(new TkOpenMetaAccess.Scope("client", "another", "facebook"));
        assertEquals(404, assertThrows(TkOpenApiException.class, () -> service.status("facebook", MEDIA)).getHttpStatus());
        verify(manager, atLeastOnce()).commit(any(TransactionStatus.class));
    }
}
