package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.time.Duration;

@Component
@Slf4j
public class TkGenerationWorkDirCleanupJob {

    @Resource
    private TkGenerationProperties generationProperties;

    @Scheduled(cron = "${tk.generation.cleanup.work-dir-cron:0 35 * * * ?}")
    public void cleanup() {
        TkGenerationProperties.Cleanup cleanup = generationProperties.getCleanup();
        if (cleanup == null || !Boolean.TRUE.equals(cleanup.getEnabled()) || Boolean.TRUE.equals(cleanup.getDryRun())) {
            return;
        }
        String configured = generationProperties.getFfmpeg().getWorkDir();
        String workDir = StrUtil.blankToDefault(configured, System.getProperty("java.io.tmpdir") + "/tk-generation")
                .replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        File root = new File(workDir).getAbsoluteFile();
        File[] children = root.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        int retentionHours = cleanup.getRenderWorkDirRetentionHours() == null
                ? 24 : Math.max(1, cleanup.getRenderWorkDirRetentionHours());
        long deadline = System.currentTimeMillis() - Duration.ofHours(retentionHours).toMillis();
        for (File child : children) {
            try {
                if (child.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)
                        && child.lastModified() < deadline) {
                    FileUtil.del(child);
                }
            } catch (Exception ex) {
                log.warn("[cleanup][renderWorkDir({}) failed]", child, ex);
            }
        }
    }
}
