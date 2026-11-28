package cn.iocoder.yudao.module.tk.service.tiktokone;

import lombok.Value;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TkTiktokOnePublishRules {

    private static final Pattern VIDEO_PATH = Pattern.compile("/@([A-Za-z0-9_.]{1,24})/video/([0-9]{1,32})/?");

    private TkTiktokOnePublishRules() {
    }

    public static Video video(String url, String creator) {
        URI uri = URI.create(url.trim());
        Matcher path = VIDEO_PATH.matcher(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"www.tiktok.com".equalsIgnoreCase(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getPort() != -1 || !path.matches()
                || !path.group(1).equalsIgnoreCase(creator)) {
            throw new IllegalArgumentException("Expected a TikTok video URL for the selected creator");
        }
        return new Video(path.group(2), "https://www.tiktok.com/@" + path.group(1) + "/video/" + path.group(2));
    }

    public static void checkConfirmation(boolean anchorRequired, boolean projectConfirmed, boolean anchorConfirmed) {
        if (anchorConfirmed && (!anchorRequired || !projectConfirmed)) {
            throw new IllegalArgumentException("An anchor requires an assigned anchor and a confirmed project");
        }
    }

    @Value
    public static class Video {
        String id;
        String url;
    }
}
