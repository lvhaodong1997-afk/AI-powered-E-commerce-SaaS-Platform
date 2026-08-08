package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DefaultTkVisualAnalysisService implements TkVisualAnalysisService {

    @Resource
    private TkGenerationProperties generationProperties;

    @Override
    public TkVisualAnalysis analyze(File videoFile, List<TkClipPlanItem> clipPlan) {
        TkVisualAnalysis visualAnalysis = analyzeByScript(videoFile, clipPlan);
        if (visualAnalysis != null) {
            return visualAnalysis;
        }
        return new TkVisualAnalysis(true, new ArrayList<>());
    }

    private TkVisualAnalysis analyzeByScript(File videoFile, List<TkClipPlanItem> clipPlan) {
        TkGenerationProperties.Visual visual = generationProperties.getSubtitle().getVisual();
        if (visual == null || !Boolean.TRUE.equals(visual.getEnabled()) || StrUtil.isBlank(visual.getScriptPath())) {
            return null;
        }
        File scriptFile = resolvePath(visual.getScriptPath());
        if (!scriptFile.isFile()) {
            return null;
        }
        File inputFile = null;
        try {
            inputFile = File.createTempFile("tk-clip-plan-", ".json");
            FileUtil.writeUtf8String(JsonUtils.toJsonString(clipPlan), inputFile);
            List<String> command = new ArrayList<>(Arrays.asList(
                    StrUtil.blankToDefault(visual.getPython(), "py"),
                    scriptFile.getAbsolutePath(),
                    "--video", videoFile.getAbsolutePath(),
                    "--clip-plan", inputFile.getAbsolutePath(),
                    "--frame-interval", String.valueOf(visual.getFrameIntervalSeconds() == null ? 1.0D : visual.getFrameIntervalSeconds())
            ));
            if (StrUtil.isNotBlank(visual.getModelPath())) {
                command.add("--model");
                command.add(visual.getModelPath());
            }
            String output = runCommand(command, visual.getTimeoutSeconds());
            return JsonUtils.parseObject(output, TkVisualAnalysis.class);
        } catch (Exception ignored) {
            return null;
        } finally {
            FileUtil.del(inputFile);
        }
    }

    private String runCommand(List<String> command, Integer timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(timeoutSeconds == null ? 300 : timeoutSeconds, TimeUnit.SECONDS);
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        if (!finished) {
            process.destroyForcibly();
            return null;
        }
        if (process.exitValue() != 0) {
            return null;
        }
        return output.toString();
    }

    private File resolvePath(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return Paths.get("").toAbsolutePath().resolve(path).normalize().toFile();
    }

}
