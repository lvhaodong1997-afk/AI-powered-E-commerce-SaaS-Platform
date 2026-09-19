package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.*;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Small crash-safe local journal for an accepted platform response. Values are encrypted before disk. */
@Component @Slf4j
public class TkOpenTiktokPublishResponseJournal {
    private final Path directory;
    private final TkOpenApiSecretCipher cipher;
    public TkOpenTiktokPublishResponseJournal(
            @Value("${tk.open-api.publish.journal-directory:${user.home}/.tk/open-publish-journal}") String directory,
            TkOpenApiSecretCipher cipher) {
        this.directory = Paths.get(directory);
        this.cipher = cipher;
    }
    public void checkWritable() {
        try {
            Files.createDirectories(directory);
            if (!Files.isWritable(directory)) throw new IOException("journal directory is not writable");
            Path probe = Files.createTempFile(directory, "probe-", ".tmp");
            Files.delete(probe);
        } catch (IOException ex) { throw new IllegalStateException("publish response journal unavailable", ex); }
    }
    public void save(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishAttemptDO attempt,
                     String publishId, String uploadUrlCipher) {
        try {
            Files.createDirectories(directory);
            Entry e = new Entry(detail.getClientId(), detail.getTaskId(), detail.getDetailId(),
                    attempt.getAttemptNo(), attempt.getOwnerToken(), publishId, uploadUrlCipher);
            Path target = entryPath(detail.getDetailId(), e.getAttemptNo());
            Path temp = Files.createTempFile(directory, "journal-", ".tmp");
            try {
                byte[] encrypted = cipher.encrypt(JsonUtils.toJsonString(e)).getBytes(StandardCharsets.UTF_8);
                try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                    ByteBuffer bytes = ByteBuffer.wrap(encrypted);
                    while (bytes.hasRemaining()) channel.write(bytes);
                    channel.force(true);
                }
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                forceDirectory();
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException ex) { throw new IllegalStateException("publish response journal unavailable", ex); }
    }
    public void remove(String detailId, int attemptNo) {
        try { Files.deleteIfExists(entryPath(detailId, attemptNo)); }
        catch (IOException ex) { throw new IllegalStateException("publish response journal cleanup failed", ex); }
    }
    public int replay(int limit, TkOpenTiktokPublishDetailMapper details, TkOpenTiktokPublishAttemptService attempts) {
        if (!Files.isDirectory(directory)) return 0;
        int count = 0;
        int scanned = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path path : stream) {
                if (scanned++ >= Math.max(1, Math.min(limit, 200))) break;
                try {
                    Entry e = JsonUtils.parseObject(cipher.decrypt(Files.readString(path)), Entry.class);
                    TkOpenTiktokPublishDetailDO detail = details.selectByClientAndDetailId(e.clientId, e.detailId);
                    if (detail == null || !Objects.equals(detail.getRetryCount() == null ? 0 : detail.getRetryCount(), e.attemptNo)) {
                        Files.move(path, path.resolveSibling(path.getFileName() + ".stale"), StandardCopyOption.REPLACE_EXISTING);
                        log.warn("[replay][retained stale publish response {}]", path.getFileName());
                        continue;
                    }
                    TkOpenTiktokPublishAttemptDO attempt = attempts.current(detail);
                    if (attempt == null || (e.ownerToken != null && !Objects.equals(attempt.getOwnerToken(), e.ownerToken))) continue;
                    attempts.saveResponse(detail, attempt, e.publishId, e.uploadUrlCipher);
                    Files.deleteIfExists(path); count++;
                } catch (Exception ex) {
                    // Retain the journal for the next process; losing accepted publish IDs is unsafe.
                    log.warn("[replay][publish response {} awaiting persistence: {}]", path.getFileName(), ex.getClass().getSimpleName());
                }
            }
        } catch (IOException ex) { throw new IllegalStateException("publish response journal scan failed", ex); }
        return count;
    }
    private Path entryPath(String detailId, int attemptNo) {
        if (detailId == null || !detailId.matches("[A-Za-z0-9_-]+") || attemptNo < 0)
            throw new IllegalArgumentException("invalid publish response journal key");
        return directory.resolve(detailId + "-" + attemptNo + ".json");
    }
    private void forceDirectory() throws IOException {
        // Linux permits syncing the directory entry; Windows does not open directories as channels.
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        }
    }
    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Entry { private String clientId, taskId, detailId; private Integer attemptNo;
        private String ownerToken, publishId, uploadUrlCipher; }
}
