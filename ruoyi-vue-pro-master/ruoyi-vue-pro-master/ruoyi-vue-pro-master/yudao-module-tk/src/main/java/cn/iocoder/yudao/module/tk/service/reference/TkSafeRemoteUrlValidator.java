package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.util.StrUtil;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.stream.Collectors;

public class TkSafeRemoteUrlValidator {

    private static final String METADATA_HOST = "169.254.169.254";

    private final Collection<String> allowedHosts;
    private final boolean blockPrivateAddress;

    public TkSafeRemoteUrlValidator(Collection<String> allowedHosts, boolean blockPrivateAddress) {
        this.allowedHosts = allowedHosts == null ? Collections.emptyList() : allowedHosts.stream()
                .filter(StrUtil::isNotBlank)
                .map(host -> host.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());
        this.blockPrivateAddress = blockPrivateAddress;
    }

    public URI validate(String rawUrl) {
        if (StrUtil.isBlank(rawUrl)) {
            throw new IllegalArgumentException("远程视频地址不能为空");
        }
        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("远程视频地址格式无效", ex);
        }
        String scheme = StrUtil.blankToDefault(uri.getScheme(), "").toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("远程视频地址只允许 HTTP/HTTPS");
        }
        if (StrUtil.isBlank(uri.getHost()) || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("远程视频地址不安全");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.isEmpty() && allowedHosts.stream().noneMatch(item -> matchesHost(host, item))) {
            throw new IllegalArgumentException("远程视频域名不在允许列表");
        }
        if (blockPrivateAddress) {
            rejectPrivateAddress(host);
        }
        return uri;
    }

    private boolean matchesHost(String host, String allowedHost) {
        return host.equals(allowedHost) || host.endsWith("." + allowedHost);
    }

    private void rejectPrivateAddress(String host) {
        if (METADATA_HOST.equals(host) || "localhost".equals(host)) {
            throw new IllegalArgumentException("远程视频地址指向受保护地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    throw new IllegalArgumentException("远程视频地址指向受保护地址");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("远程视频域名无法解析", ex);
        }
    }
}
