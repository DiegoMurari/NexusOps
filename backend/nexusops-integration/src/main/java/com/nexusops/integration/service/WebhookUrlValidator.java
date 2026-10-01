package com.nexusops.integration.service;

import com.nexusops.shared.exception.ValidationException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Static SSRF guard for webhook targets: https only, no credentials in the URL, and no host that is
 * obviously internal (single-label names, internal suffixes, loopback/private/link-local literals).
 * It cannot defend against DNS rebinding, so the future delivery code must re-check the resolved
 * address before connecting.
 */
final class WebhookUrlValidator {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern NUMERIC_OR_HEX = Pattern.compile("^(0x[0-9a-f]+|\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final List<String> INTERNAL_SUFFIXES =
        List.of(".local", ".localhost", ".internal", ".lan", ".home", ".corp", ".intranet");

    private WebhookUrlValidator() {
    }

    static String validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ValidationException("Webhook URL is required");
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new ValidationException("Webhook URL is not valid");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ValidationException("Webhook URL must use https");
        }
        if (uri.getUserInfo() != null) {
            throw new ValidationException("Webhook URL must not contain credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException("Webhook URL must have a host");
        }
        host = host.toLowerCase();
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            rejectInternalAddress(host.substring(1, host.length() - 1));
        } else if (IPV4.matcher(host).matches()) {
            rejectInternalAddress(host);
        } else {
            if (NUMERIC_OR_HEX.matcher(host).matches() || !host.contains(".")
                || INTERNAL_SUFFIXES.stream().anyMatch(host::endsWith)) {
                throw new ValidationException("Webhook host is not allowed");
            }
        }
        return uri.toString();
    }

    private static void rejectInternalAddress(String literal) {
        InetAddress address;
        try {
            address = InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new ValidationException("Webhook host is not valid");
        }
        byte[] bytes = address.getAddress();
        boolean uniqueLocalV6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocalV6) {
            throw new ValidationException("Webhook host is not allowed");
        }
    }
}
