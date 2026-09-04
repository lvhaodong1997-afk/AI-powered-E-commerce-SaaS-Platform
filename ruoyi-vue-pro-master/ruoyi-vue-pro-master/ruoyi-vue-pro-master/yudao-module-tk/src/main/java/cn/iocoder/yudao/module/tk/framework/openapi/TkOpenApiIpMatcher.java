package cn.iocoder.yudao.module.tk.framework.openapi;

import java.net.InetAddress;

public final class TkOpenApiIpMatcher {

    private TkOpenApiIpMatcher() {
    }

    public static boolean matches(String allowedIps, String clientIp) {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            return true;
        }
        if (clientIp == null || clientIp.trim().isEmpty()) {
            return false;
        }
        for (String rule : allowedIps.split("[,;\\s]+")) {
            if (rule.equals(clientIp) || cidrMatches(rule, clientIp)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isValidRules(String allowedIps) {
        if (allowedIps == null || allowedIps.trim().isEmpty()) {
            return true;
        }
        for (String rule : allowedIps.split("[,;\\s]+")) {
            int separator = rule.indexOf('/');
            String addressText = separator < 0 ? rule : rule.substring(0, separator);
            if (!addressText.matches("[0-9A-Fa-f:.]+")) {
                return false;
            }
            try {
                byte[] address = InetAddress.getByName(addressText).getAddress();
                if (separator >= 0) {
                    int bits = Integer.parseInt(rule.substring(separator + 1));
                    if (bits < 0 || bits > address.length * 8) {
                        return false;
                    }
                }
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    private static boolean cidrMatches(String rule, String clientIp) {
        int separator = rule.indexOf('/');
        if (separator <= 0) {
            return false;
        }
        try {
            byte[] network = InetAddress.getByName(rule.substring(0, separator)).getAddress();
            byte[] address = InetAddress.getByName(clientIp).getAddress();
            int bits = Integer.parseInt(rule.substring(separator + 1));
            if (network.length != address.length || bits < 0 || bits > network.length * 8) {
                return false;
            }
            for (int index = 0; index < network.length; index++) {
                int remaining = bits - index * 8;
                if (remaining <= 0) {
                    return true;
                }
                int mask = remaining >= 8 ? 0xff : (0xff << (8 - remaining)) & 0xff;
                if ((network[index] & mask) != (address[index] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
