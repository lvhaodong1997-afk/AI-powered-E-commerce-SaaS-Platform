package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishTaskMapper;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.upload.TkGenerationOutputStorageService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class TkSocialMediaService {
    public static final long MAX_VIDEO_BYTES = 100L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private final FileApi files;
    private final TkSocialMediaMapper mapper;
    private final TkDataScopeService scope;
    // FileApi accepts bytes; bound simultaneous allocations for this integration.
    private final Semaphore uploadSlot = new Semaphore(1);
    @Resource private TkSocialVideoInspector videoInspector;
    @Resource private TkGenerationTaskService generationService;
    @Resource private TkGenerationOutputStorageService outputStorage;
    @Resource private TkSocialPublishTaskMapper tasks;
    @Resource private PlatformTransactionManager transactionManager;
    @Value("${tk.social.enabled:false}") private boolean enabled;

    public TkSocialMediaService(FileApi files, TkSocialMediaMapper mapper, TkDataScopeService scope) {
        this.files = files; this.mapper = mapper; this.scope = scope;
    }

    public TkSocialMediaDO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择图片或视频");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        boolean video = name.toLowerCase(Locale.ROOT).endsWith(".mp4");
        if (!video && !name.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")) throw new IllegalArgumentException("支持 MP4 视频和 JPEG 图片");
        long limit = video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (file.getSize() > limit) throw new IllegalArgumentException(video ? "视频不能超过 100MB" : "图片不能超过 8MB");
        if (!uploadSlot.tryAcquire()) throw new IllegalArgumentException("已有媒体正在上传，请稍后重试");
        try (InputStream input = file.getInputStream()) {
            return save(readBounded(input, limit), name, video);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传文件失败");
        } finally { uploadSlot.release(); }
    }

    public TkSocialMediaDO importGenerated(Long taskId) {
        TkGenerationTaskDO task = generationService.getGenerationTask(taskId);
        if (task == null || !"SUCCESS".equals(task.getStatus()) || task.getOutputUrl() == null) {
            throw new IllegalArgumentException("请先完成视频生成");
        }
        scope.validateReadable(task.getTenantId(), task.getCompanyId(), task.getCreator());
        String readUrl = outputStorage.refreshGeneratedAssetReadUrl(task, task.getOutputUrl());
        validateReadUrl(readUrl);
        if (!uploadSlot.tryAcquire()) throw new IllegalArgumentException("已有媒体正在上传，请稍后重试");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(readUrl).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            if (connection.getResponseCode() != 200) throw new IllegalArgumentException("生成视频读取失败，请检查存储访问配置");
            if (connection.getContentLengthLong() > MAX_VIDEO_BYTES) throw new IllegalArgumentException("视频不能超过 100MB");
            try (InputStream input = connection.getInputStream()) {
                return save(readBounded(input, MAX_VIDEO_BYTES), "generation-" + taskId + ".mp4", true);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取生成视频失败，请稍后重试");
        } finally {
            if (connection != null) connection.disconnect();
            uploadSlot.release();
        }
    }

    private TkSocialMediaDO save(byte[] bytes, String name, boolean video) {
        int width, height;
        double duration = 0;
        double frameRate = 0;
        if (video) {
            if (bytes.length < 12 || !"ftyp".equals(new String(bytes, 4, 4, StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("文件不是有效的 MP4 视频");
            }
            bytes = videoInspector.prepareForPublish(bytes);
            TkSocialVideoInspector.Metadata metadata = videoInspector.inspect(bytes);
            width = metadata.getWidth(); height = metadata.getHeight(); duration = metadata.getDurationSeconds();
            frameRate = metadata.getFrameRate();
        } else {
            int[] size = inspectJpeg(bytes);
            width = size[0]; height = size[1];
        }
        TkUserScope current = scope.getCurrentScope();
        Long companyId = scope.getWritableCompanyId(null);
        if (!current.hasTenantScope() || companyId == null) throw new IllegalArgumentException("请先选择租户");
        String type = video ? "video/mp4" : "image/jpeg";
        String url = files.createFile(bytes, UUID.randomUUID() + (video ? ".mp4" : ".jpg"),
                "tk/" + current.getTenantId() + "/" + companyId + "/social-media", type);
        try {
            validateReadUrl(resolveReadableUrl(url));
            TkSocialMediaDO media = new TkSocialMediaDO();
            media.setTenantId(current.getTenantId()); media.setCompanyId(companyId); media.setCreator(current.getUserIdString());
            media.setFileName(name.substring(0, Math.min(255, name.length()))); media.setContentType(type);
            media.setMediaType(video ? "VIDEO" : "IMAGE"); media.setFileSize((long)bytes.length);
            media.setWidth(width); media.setHeight(height); media.setDurationSeconds(duration);
            media.setFrameRate(frameRate);
            media.setPublicUrl(url); media.setStatus("READY");
            mapper.insert(media);
            return media;
        } catch (RuntimeException ex) {
            try { files.deleteFileByUrl(url); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    public TkSocialMediaDO requireReadable(Long id) {
        TkSocialMediaDO media = mapper.selectById(id);
        if (media == null) throw new IllegalArgumentException("媒体不存在");
        scope.validateReadable(media.getTenantId(), media.getCompanyId(), media.getCreator());
        if (!"READY".equals(media.getStatus())) throw new IllegalArgumentException("媒体尚未就绪");
        return media;
    }

    /** Must run in the task-creation transaction; serializes publication with cleanup. */
    public void lockReady(Long id,Long tenantId) {
        TkSocialMediaDO source=mapper.lockMedia(id,tenantId);
        if (source==null || !"READY".equals(source.getStatus())) throw new IllegalArgumentException("媒体已过期或清理中，请重新上传");
    }

    public void cleanupUnreferenced(Long id,Long tenantId) {
        TkSocialMediaDO candidate=new TransactionTemplate(transactionManager).execute(status -> {
            TkSocialMediaDO source=mapper.lockMedia(id,tenantId);
            if (source==null || !Arrays.asList("READY","DELETING").contains(source.getStatus())) return null;
            if (tasks.selectCount(new QueryWrapper<TkSocialPublishTaskDO>().eq("tenant_id",tenantId).eq("media_id",id))>0) return null;
            mapper.update(null,new UpdateWrapper<TkSocialMediaDO>().eq("id",id).eq("tenant_id",tenantId).set("status","DELETING"));
            return source;
        });
        if (candidate==null) return;
        // Claim is committed before storage I/O. A crashed deletion is retried by the sweep.
        files.deleteFileByUrl(candidate.getPublicUrl());
        mapper.update(null,new UpdateWrapper<TkSocialMediaDO>().eq("id",id).eq("tenant_id",tenantId).eq("status","DELETING")
                .set("status","DELETED").set("deleted",true));
    }

    @Scheduled(fixedDelay=3600000,initialDelay=120000)
    public void cleanupAbandonedMedia() {
        if (!enabled) return;
        List<TkSocialMediaDO> candidates=TenantUtils.executeIgnore(() -> mapper.selectList(
                new QueryWrapper<TkSocialMediaDO>().and(q -> q.eq("status","DELETING")
                        .or(s -> s.eq("status","READY").lt("create_time",LocalDateTime.now().minusHours(24))))
                        .notInSql("id","SELECT media_id FROM tk_social_publish_task WHERE media_id IS NOT NULL AND deleted=0")
                        .orderByAsc("id").last("LIMIT 20")));
        for (TkSocialMediaDO candidate:candidates) {
            try { TenantUtils.execute(candidate.getTenantId(),() -> cleanupUnreferenced(candidate.getId(),candidate.getTenantId())); }
            catch (RuntimeException ignored) { /* Keep the row for the next bounded sweep; never log signed URLs. */ }
        }
    }

    public String readUrl(TkSocialMediaDO media) {
        if (media == null) return null;
        String url = resolveReadableUrl(media.getPublicUrl());
        validateReadUrl(url);
        return url;
    }

    private String resolveReadableUrl(String url) {
        try {
            String readableUrl = files.presignGetUrl(url, 86400);
            return readableUrl == null || readableUrl.trim().isEmpty() ? url : readableUrl;
        } catch (UnsupportedOperationException ex) {
            return url;
        }
    }

    public static void validateForPlatform(TkSocialMediaDO media,String platform) {
        if (media==null || !"VIDEO".equals(media.getMediaType())) return;
        Integer w=media.getWidth(),h=media.getHeight();
        Double seconds=media.getDurationSeconds(),fps=media.getFrameRate();
        if (w==null || h==null || seconds==null || fps==null || !Double.isFinite(seconds) || !Double.isFinite(fps)
                || w<1 || h<1 || fps<23 || fps>60) {
            throw new IllegalArgumentException("视频帧率需为 23–60fps，请重新上传符合要求的视频");
        }
        if ("FACEBOOK_PAGE".equals(platform)) {
            if (w<540 || h<960 || Math.abs((double)w/h-9.0/16)>0.002 || seconds<4 || seconds>60) {
                throw new IllegalArgumentException("Facebook Reels 视频需为 9:16 竖屏、至少 540×960、时长 4–60 秒");
            }
        } else if ("INSTAGRAM".equals(platform) && (w>1920 || seconds<3 || seconds>900)) {
            throw new IllegalArgumentException("Instagram 视频宽度不超过 1920 像素，时长 3 秒至 15 分钟");
        }
    }

    static byte[] readBounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[65536]; int count;
        while ((count = input.read(chunk)) != -1) {
            if ((long)buffer.size() + count > limit) throw new IllegalArgumentException("文件超过大小限制");
            buffer.write(chunk, 0, count);
        }
        return buffer.toByteArray();
    }

    static int[] inspectJpeg(byte[] bytes) {
        if (bytes.length < 3 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216) throw new IllegalArgumentException("请上传有效的 JPEG 图片");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("无法识别 JPEG 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if ((long)w * h > 40_000_000 || w < 1 || h < 1 || (double)w / h < 0.8 || (double)w / h > 1.91) {
                    throw new IllegalArgumentException("图片宽高比需为 4:5 至 1.91:1，且不超过 4000 万像素");
                }
                reader.read(0);
                return new int[]{w,h};
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("无法解析 JPEG 图片"); }
    }

    public static void validateReadUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || host.equalsIgnoreCase("localhost") || host.endsWith(".local")
                    || host.contains(":") || host.matches("[0-9.]+")) {
                throw new IllegalArgumentException("媒体存储需要配置公网 HTTPS 域名");
            }
        } catch (NullPointerException ex) { throw new IllegalArgumentException("媒体存储地址未配置"); }
    }
}
