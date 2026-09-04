package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.hutool.core.util.StrUtil;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class TkOpenApiCallbackUrlValidator {

    private TkOpenApiCallbackUrlValidator() {
    }

    public static void validate(String url) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Callback URL is invalid", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || StrUtil.isBlank(uri.getHost())
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Callback URL must be a public HTTPS URL");
        }
        String host = uri.getHost();
        if ("localhost".equalsIgnoreCase(host) || host.toLowerCase().endsWith(".localhost")) {
            throw new IllegalArgumentException("Callback URL cannot target a private address");
        }
        if (host.matches("[0-9A-Fa-f:.]+")) {
            try {
                rejectPrivate(InetAddress.getByName(host));
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Callback URL host is invalid", ex);
            }
        }
    }

    public static void validateResolved(String url) {
        resolveAndValidate(url);
    }

    public static InetAddress[] resolveAndValidate(String url) {
        return resolveAndValidate(url, InetAddress::getAllByName);
    }

    static InetAddress[] resolveAndValidate(String url, HostResolver resolver) {
        validate(url);
        try {
            InetAddress[] addresses = resolver.resolve(URI.create(url).getHost());
            if (addresses == null || addresses.length == 0) {
                throw new IllegalArgumentException("Callback URL host cannot be resolved");
            }
            for (InetAddress address : addresses) {
                rejectPrivate(address);
            }
            return addresses.clone();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Callback URL host cannot be resolved", ex);
        }
    }

    private static void rejectPrivate(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean uniqueLocalIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocalIpv6) {
            throw new IllegalArgumentException("Callback URL cannot target a private address");
        }
    }

    @FunctionalInterface
    interface HostResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
