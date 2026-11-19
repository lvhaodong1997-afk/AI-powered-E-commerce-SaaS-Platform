package cn.iocoder.yudao.module.tk.service.channel;

import java.net.URI;

final class TkChannelProtocol {
    private TkChannelProtocol() { }

    static long nextByte(String range, long size) {
        if (range == null) return 0;
        if (!range.matches("bytes=0-[0-9]{1,12}")) throw new IllegalArgumentException("Invalid upload range");
        long next = Long.parseLong(range.substring(8)) + 1;
        if (next >= size) throw new IllegalArgumentException("Invalid upload offset");
        return next;
    }

    static URI uploadUri(String location) {
        if (location == null) throw new IllegalArgumentException("Missing upload URL");
        URI uri = URI.create(location);
        if (!"https".equals(uri.getScheme()) || !"www.googleapis.com".equals(uri.getHost())
                || uri.getPort() != -1 || uri.getRawUserInfo() != null || uri.getFragment() != null
                || !"/upload/youtube/v3/videos".equals(uri.getRawPath()) || uri.getRawQuery() == null) {
            throw new IllegalArgumentException("Invalid upload URL");
        }
        return uri;
    }

    static String failureState(int status, boolean uncertainWrite) {
        if (status == 401) return "REAUTH_REQUIRED";
        if (status == 429) return "PENDING";
        if (status == 0 || status == 408 || status >= 500) return uncertainWrite ? "UNKNOWN" : "PENDING";
        return "FAILED";
    }
}
