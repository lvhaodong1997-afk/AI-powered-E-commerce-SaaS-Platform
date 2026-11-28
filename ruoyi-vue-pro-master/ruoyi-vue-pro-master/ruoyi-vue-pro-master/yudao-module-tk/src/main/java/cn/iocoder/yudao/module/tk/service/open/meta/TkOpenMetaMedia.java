package cn.iocoder.yudao.module.tk.service.open.meta;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.tk.service.open.meta.TkOpenMetaAuth.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TkOpenMetaMedia {
    static final int CHUNK = 4 * 1024 * 1024;
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final TkOpenMetaAccess access;

    public Map<String, Object> start(String platform, String contentType, long size, String hash) {
        TkOpenMetaAccess.Scope s = access.require(platform, "publish");
        validate(contentType, size, hash);
        access.baseUrl();
        Path root = root();
        if (!Files.isWritable(root)) throw TkOpenApiException.unavailable("META_MEDIA_UNAVAILABLE", "Private storage is not writable");
        String id = TkOpenApiIds.next("metamedia");
        db.update("INSERT INTO tk_open_meta_media (id,client_id,workspace_id,platform,content_type,file_size,sha256,status,expires_at) "
                + "VALUES (?,?,?,?,?,?,?,'UPLOADING',?)", id, s.clientId, s.workspaceId, platform, contentType, size, hash,
                LocalDateTime.now().plusHours(24));
        return status(s, id);
    }

    static void validate(String type, long size, String hash) {
        long limit = "video/mp4".equals(type) ? 100_000_000 : "image/jpeg".equals(type) ? 8_000_000 : 0;
        if (size < 1 || size > limit || hash == null || !hash.matches("[0-9a-f]{64}"))
            throw invalid("Supply a JPEG (8 MB) or MP4 (100 MB) and lower-case file SHA-256");
    }

    public Map<String, Object> status(String platform, String id) { return status(access.require(platform, "publish"), id); }

    public void chunk(String platform, String id, int index, byte[] bytes) {
        TkOpenMetaAccess.Scope s = access.require(platform, "publish");
        transactions.executeWithoutResult(tx -> {
            Map<String, Object> row = find(s, id, true);
            requireUploading(row);
            long size = size(row);
            if (index < 0 || index >= total(size) || bytes == null || bytes.length != Math.min(CHUNK, size - (long) index * CHUNK))
                throw invalid("Chunk index or byte length does not match the upload");
            try {
                Path dir = directory(id);
                if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(dir);
                if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Not a private media directory");
                Path part = dir.resolve(index + ".part");
                if (Files.exists(part, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Arrays.equals(bytes, Files.readAllBytes(part)))
                        throw TkOpenApiException.conflict("META_CHUNK_CONFLICT", "Chunk has already been uploaded with different bytes");
                    return;
                }
                Path temporary = Files.createTempFile(dir, ".chunk-", ".tmp");
                try {
                    Files.write(temporary, bytes);
                    Files.move(temporary, part, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException ex) { throw storageError(); }
        });
    }

    public Map<String, Object> complete(String platform, String id) {
        TkOpenMetaAccess.Scope s = access.require(platform, "publish");
        return transactions.execute(tx -> {
            Map<String, Object> row = find(s, id, true);
            if (!"READY".equals(row.get("status"))) {
                requireUploading(row);
                assemble(row);
                db.update("UPDATE tk_open_meta_media SET status='READY' WHERE id=?", id);
            }
            return status(s, id);
        });
    }

    Map<String, Object> ready(TkOpenMetaAccess.Scope s, String id) {
        Map<String, Object> row = find(s, id, false);
        if (!"READY".equals(row.get("status")) || !time(row, "expires_at").isAfter(LocalDateTime.now()))
            throw invalid("Media is not ready or has expired");
        return row;
    }

    Path file(String id) {
        Path dir = directory(id);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) throw storageError();
        return dir.resolve("media");
    }

    private void assemble(Map<String, Object> row) {
        String id = str(row, "id");
        Path dir = directory(id);
        try {
            long size = size(row);
            for (int i = 0; i < total(size); i++) if (!Files.isRegularFile(dir.resolve(i + ".part"), LinkOption.NOFOLLOW_LINKS))
                throw invalid("Upload is missing chunks");
            Path assembled = Files.createTempFile(dir, ".media-", ".tmp");
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (OutputStream out = new DigestOutputStream(Files.newOutputStream(assembled), digest)) {
                    for (int i = 0; i < total(size); i++) Files.copy(dir.resolve(i + ".part"), out);
                }
                if (Files.size(assembled) != size || !hex(digest.digest()).equals(row.get("sha256"))) throw invalid("File SHA-256 mismatch");
                checkFormat(str(row, "content_type"), assembled);
                Files.move(assembled, file(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(assembled); }
        } catch (IOException | NoSuchAlgorithmException ex) { throw storageError(); }
    }

    static void checkFormat(String contentType, Path file) throws IOException {
        if ("image/jpeg".equals(contentType)) {
            try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) throw invalid("The image is not JPEG");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in, true, true);
                    int w = reader.getWidth(0), h = reader.getHeight(0);
                    if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || w < 1 || h < 1 || w > 8192 || h > 8192
                            || (long) w * h > 16_000_000) throw invalid("Invalid JPEG format or dimensions");
                    reader.read(0);
                } finally { reader.dispose(); }
            } catch (IOException ex) { throw invalid("The JPEG cannot be decoded"); }
        } else {
            try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
                int boxSize = in.readInt();
                if (boxSize < 16 || boxSize > Files.size(file) || in.readInt() != 0x66747970) throw invalid("The video is not MP4");
            } catch (EOFException ex) { throw invalid("The video is not MP4"); }
        }
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    public void cleanup() {
        if (!access.enabled("facebook") && !access.enabled("instagram")) return;
        for (String id : db.queryForList("SELECT m.id FROM tk_open_meta_media m WHERE m.expires_at<CURRENT_TIMESTAMP(3) AND NOT EXISTS "
                + "(SELECT 1 FROM tk_open_meta_task t WHERE t.media_id=m.id AND t.status IN ('PENDING','PROCESSING')) LIMIT 100", String.class)) {
            transactions.executeWithoutResult(tx -> {
                if (db.update("UPDATE tk_open_meta_media SET status='EXPIRED' WHERE id=? AND expires_at<CURRENT_TIMESTAMP(3)", id) != 1) return;
                Path dir = directory(id);
                try {
                    if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
                        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
                            for (Path entry : entries) Files.delete(entry);
                        }
                        Files.delete(dir);
                    }
                    db.update("DELETE FROM tk_open_meta_media WHERE id=? AND status='EXPIRED'", id);
                } catch (IOException ex) { log.warn("Could not clean expired Meta media: id={}", id); }
            });
        }
    }

    private Map<String, Object> status(TkOpenMetaAccess.Scope s, String id) {
        Map<String, Object> row = find(s, id, false);
        String status = time(row, "expires_at").isAfter(LocalDateTime.now()) ? str(row, "status") : "EXPIRED";
        return result("uploadId", id, "workspaceId", s.workspaceId, "status", status, "uploadMode", "LOCAL",
                "chunkSize", CHUNK, "totalChunks", total(size(row)), "fileSize", size(row), "expireTime", time(row, "expires_at"),
                "mediaId", "READY".equals(status) ? id : null);
    }

    private Map<String, Object> find(TkOpenMetaAccess.Scope s, String id, boolean lock) {
        Map<String, Object> row = one(db.queryForList("SELECT * FROM tk_open_meta_media" + SCOPED + (lock ? " FOR UPDATE" : ""), s.args(id)));
        if (row == null) throw TkOpenApiException.notFound("META_MEDIA_NOT_FOUND", "Upload or media not found");
        return row;
    }

    private Path root() {
        Path path = access.privateRoot();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw storageError();
        return path;
    }
    private Path directory(String id) {
        if (!id.matches("metamedia_[0-9a-f]{32}")) throw invalid("Invalid media ID");
        return root().resolve(id);
    }
    private static void requireUploading(Map<String, Object> row) {
        if (!"UPLOADING".equals(row.get("status")) || !time(row, "expires_at").isAfter(LocalDateTime.now())) throw invalid("Upload is closed or expired");
    }
    private static int total(long size) { return (int) ((size + CHUNK - 1) / CHUNK); }
    private static long size(Map<String, Object> row) { return ((Number) row.get("file_size")).longValue(); }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(Character.forDigit((b & 255) >>> 4, 16)).append(Character.forDigit(b & 15, 16));
        return result.toString();
    }
    private static TkOpenApiException storageError() { return TkOpenApiException.unavailable("META_MEDIA_UNAVAILABLE", "Private media storage is unavailable"); }
    private static TkOpenApiException invalid(String message) { return TkOpenApiException.badRequest("META_MEDIA_INVALID", message); }
}
