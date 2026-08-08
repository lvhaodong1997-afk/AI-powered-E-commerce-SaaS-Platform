package cn.iocoder.yudao.module.tk.framework.ffmpeg;

import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves FFmpeg executables even when the process PATH was not prepared by the launcher.
 */
public final class TkFfmpegExecutableResolver {

    private static final String WINDOWS_TOOL_ROOT = "D:/lhd/test/.tools/ffmpeg";

    private TkFfmpegExecutableResolver() {
    }

    public static String ffmpeg(String configuredPath) {
        return resolve("ffmpeg", configuredPath, "FFMPEG_PATH");
    }

    public static String ffprobe(String configuredPath) {
        return resolve("ffprobe", configuredPath, "FFPROBE_PATH");
    }

    public static String parentDir(String executable) {
        if (StrUtil.isBlank(executable)) {
            return null;
        }
        Path path = Paths.get(executable);
        Path parent = path.getParent();
        return parent == null ? null : parent.toString();
    }

    private static String resolve(String command, String configuredPath, String envName) {
        String executableName = executableName(command);
        String configured = trimToNull(configuredPath);
        String resolved = firstExecutable(
                configured,
                trimToNull(System.getenv(envName)),
                findInProjectTools(executableName),
                findInPath(executableName),
                findInPath(command));
        if (StrUtil.isNotBlank(resolved)) {
            return resolved;
        }
        throw new IllegalStateException(StrUtil.format(
                "未找到 {} 可执行文件。请配置 {} 或 tk.generation.ffmpeg.{}-path，当前配置值：{}",
                command, envName, command, StrUtil.blankToDefault(configured, "<空>")));
    }

    private static String firstExecutable(String... candidates) {
        for (String candidate : candidates) {
            String executable = executable(candidate);
            if (StrUtil.isNotBlank(executable)) {
                return executable;
            }
        }
        return null;
    }

    private static String executable(String rawPath) {
        String pathText = trimToNull(rawPath);
        if (pathText == null) {
            return null;
        }
        Path path = Paths.get(pathText);
        if (!path.isAbsolute() && containsSeparator(pathText)) {
            path = Paths.get("").toAbsolutePath().resolve(path);
        }
        if (Files.isRegularFile(path)) {
            return path.toAbsolutePath().normalize().toString();
        }
        return null;
    }

    private static String findInProjectTools(String executableName) {
        Path root = Paths.get(WINDOWS_TOOL_ROOT);
        Path direct = root.resolve("ffmpeg-8.1.1-essentials_build").resolve("bin").resolve(executableName);
        String resolved = executable(direct.toString());
        if (StrUtil.isNotBlank(resolved)) {
            return resolved;
        }
        File rootFile = root.toFile();
        File[] versions = rootFile.isDirectory() ? rootFile.listFiles(File::isDirectory) : null;
        if (versions == null) {
            return null;
        }
        for (File version : versions) {
            resolved = executable(version.toPath().resolve("bin").resolve(executableName).toString());
            if (StrUtil.isNotBlank(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    private static String findInPath(String executableName) {
        String pathEnv = System.getenv("PATH");
        if (StrUtil.isBlank(pathEnv)) {
            return null;
        }
        for (String entry : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            String directory = trimToNull(entry);
            if (directory == null) {
                continue;
            }
            String resolved = executable(Paths.get(directory).resolve(executableName).toString());
            if (StrUtil.isNotBlank(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    private static String executableName(String command) {
        return isWindows() ? command + ".exe" : command;
    }

    private static boolean containsSeparator(String value) {
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0;
    }

    private static boolean isWindows() {
        return StrUtil.containsIgnoreCase(System.getProperty("os.name"), "win");
    }

    private static String trimToNull(String value) {
        String trimmed = StrUtil.trimToNull(value);
        if (trimmed == null || StrUtil.equals(trimmed, "\"\"") || StrUtil.equals(trimmed, "''")) {
            return null;
        }
        return StrUtil.removeSuffix(StrUtil.removePrefix(trimmed, "\""), "\"");
    }
}
