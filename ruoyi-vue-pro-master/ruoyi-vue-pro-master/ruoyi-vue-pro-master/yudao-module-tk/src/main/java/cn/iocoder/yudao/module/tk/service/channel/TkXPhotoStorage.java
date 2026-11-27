package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Service
public class TkXPhotoStorage {
    public static final int MAX_BYTES = 5_000_000;
    private static final int MAX_PIXELS = 16_000_000;
    private static final long RETENTION_HOURS = 24;

    @Value("${tk.x-photo.private-root:${java.io.tmpdir}/tk-x-photo-private}")
    private String directory;

    public static boolean accepts(String type) {
        return "image/jpeg".equals(type) || "image/png".equals(type);
    }

    public static void validateIds(List<String> ids) {
        if (ids.isEmpty() || ids.size() > 4 || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(id -> id == null || !id.matches("[A-Za-z0-9_-]{1,64}")))
            throw new IllegalArgumentException("Supply one to four distinct image IDs");
    }

    public Path openUpload(String uploadId) {
        return folder("open", uploadId);
    }

    public static String workbench(Long tenant, Long company, String owner) {
        return TkSocialAuthService.hash(tenant + ":" + company + ":" + owner);
    }

    public String save(String scope, InputStream source, String type) throws IOException {
        if (!accepts(type)) throw new IllegalArgumentException("JPEG or PNG required");
        byte[] bytes = source.readNBytes(MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Image exceeds size limit");
        String id = "photo_" + UUID.randomUUID().toString().replace("-", "");
        Path folder = folder(scope, id);
        createPrivate(folder);
        Path target = folder.resolve("image");
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            validate(target, type);
            return id;
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(target);
            Files.deleteIfExists(folder);
            throw ex;
        }
    }

    public Path require(String scope, String id) {
        Path image = folder(scope, id).resolve("image");
        try {
            if (Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS)
                    && Files.getLastModifiedTime(image, LinkOption.NOFOLLOW_LINKS).toInstant().isAfter(cutoff())) return image;
        } catch (IOException ignored) { }
        throw new IllegalArgumentException("Image is missing or expired");
    }

    public void createPrivate(Path folder) throws IOException {
        for (Path path : List.of(root(), folder.getParent(), folder)) {
            Files.createDirectories(path);
            if (Files.isSymbolicLink(path)) throw new IOException("Unsafe image directory");
            if (Files.getFileStore(path).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    public byte[] read(Path path) {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > 0 && bytes.length <= MAX_BYTES) return bytes;
        } catch (IOException ignored) { }
        throw new IllegalArgumentException("Image cannot be read");
    }

    public static void validate(Path path, String expected) throws IOException {
        if (!accepts(expected) || Files.size(path) < 1 || Files.size(path) > MAX_BYTES)
            throw new IllegalArgumentException("JPEG or PNG up to 5 MB required");
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) throw new IllegalArgumentException("Invalid image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Invalid image");
            ImageReader reader = readers.next();
            try {
                if (!("image/" + reader.getFormatName().toLowerCase(Locale.ROOT)).equals(expected))
                    throw new IllegalArgumentException("Image content does not match its declared type");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > MAX_PIXELS)
                    throw new IllegalArgumentException("Image dimensions exceed limits");
                reader.read(0).flush();
            } finally { reader.dispose(); }
        }
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void sweep() throws IOException {
        if (!Files.isDirectory(root(), LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> paths = Files.find(root(), 2, (path, attrs) -> attrs.isDirectory()
                && root().relativize(path).getNameCount() == 2 && attrs.lastModifiedTime().toInstant().isBefore(cutoff()))) {
            for (Path folder : (Iterable<Path>) paths.limit(500)::iterator) {
                try (Stream<Path> files = Files.list(folder)) {
                    List<Path> contents = files.collect(java.util.stream.Collectors.toList());
                    if (contents.stream().anyMatch(path -> path.toFile().lastModified() >= cutoff().toEpochMilli())) continue;
                    for (Path path : contents) Files.deleteIfExists(path);
                    Files.deleteIfExists(folder);
                } catch (IOException ignored) { /* Retry on the next sweep. */ }
            }
        }
    }

    private Path root() { return Path.of(directory).toAbsolutePath().normalize(); }
    private static Instant cutoff() { return Instant.now().minus(RETENTION_HOURS, ChronoUnit.HOURS); }

    private Path folder(String scope, String id) {
        if (!scope.matches("open|[a-f0-9]{64}") || id == null || !id.matches("(?:upload|photo)_[a-f0-9]{32}"))
            throw new IllegalArgumentException("Invalid image identifier");
        return root().resolve(scope).resolve(id);
    }
}
