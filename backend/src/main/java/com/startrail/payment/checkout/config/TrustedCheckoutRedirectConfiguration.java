package com.startrail.payment.checkout.config;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public class TrustedCheckoutRedirectConfiguration {
    private static final String ORDER_PLACEHOLDER = "{businessOrderId}";
    private static final String ORDER_PLACEHOLDER_MARKER = "startrail-order-placeholder";

    private final String publicBaseUrl;
    private final String businessOrderUrlTemplate;
    private final boolean production;

    public TrustedCheckoutRedirectConfiguration(
            @Value("${payment.checkout.public-base-url}") String publicBaseUrl,
            @Value("${startrail.integration.business-order-url-template}") String businessOrderUrlTemplate,
            Environment environment) {
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
        this.businessOrderUrlTemplate = validateBusinessOrderUrlTemplate(businessOrderUrlTemplate);
        this.production = environment.matchesProfiles("production");
        if (production) {
            requireProductionUrl(this.publicBaseUrl, "payment.checkout.public-base-url");
            requireProductionUrl(this.businessOrderUrlTemplate.replace(ORDER_PLACEHOLDER, "order-id"),
                    "startrail.integration.business-order-url-template");
        }
    }

    public String checkoutUrl(String opaqueToken) {
        return publicBaseUrl + "/checkout/" + opaqueToken;
    }

    public String businessOrderUrl(String businessOrderId) {
        if (businessOrderId == null || businessOrderId.isBlank()
                || businessOrderId.equals(".") || businessOrderId.equals("..")) {
            throw new IllegalArgumentException("businessOrderId 不能是空值或路径导航片段");
        }
        var encodedOrderId = UriUtils.encodePathSegment(businessOrderId, StandardCharsets.UTF_8);
        return businessOrderUrlTemplate.replace(ORDER_PLACEHOLDER, encodedOrderId);
    }

    private String normalizeBaseUrl(String value) {
        var normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
        var uri = parseAbsoluteUrl(normalized, "payment.checkout.public-base-url");
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException("payment.checkout.public-base-url 不能包含查询参数或片段");
        }
        if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) {
            throw new IllegalStateException("payment.checkout.public-base-url 必须使用 HTTP 或 HTTPS");
        }
        return normalized;
    }

    private String validateBusinessOrderUrlTemplate(String value) {
        if (value == null || value.isBlank()
                || !value.contains(ORDER_PLACEHOLDER)
                || value.indexOf(ORDER_PLACEHOLDER) != value.lastIndexOf(ORDER_PLACEHOLDER)) {
            throw new IllegalStateException(
                    "startrail.integration.business-order-url-template 必须且只能包含 {businessOrderId}");
        }
        var sampleUrl = value.replace(ORDER_PLACEHOLDER, ORDER_PLACEHOLDER_MARKER);
        var uri = parseAbsoluteUrl(sampleUrl, "startrail.integration.business-order-url-template");
        var rawPath = uri.getRawPath();
        var isPathSegment = rawPath != null
                && Arrays.stream(rawPath.split("/", -1))
                        .anyMatch(ORDER_PLACEHOLDER_MARKER::equals);
        var markerInQueryOrFragment = contains(uri.getRawQuery(), ORDER_PLACEHOLDER_MARKER)
                || contains(uri.getRawFragment(), ORDER_PLACEHOLDER_MARKER);
        if (!isPathSegment || markerInQueryOrFragment
                || contains(uri.getHost(), ORDER_PLACEHOLDER_MARKER)) {
            throw new IllegalStateException(
                    "startrail.integration.business-order-url-template 的 {businessOrderId} 必须独占路径段");
        }
        if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) {
            throw new IllegalStateException("星辙业务订单返回地址必须使用 HTTP 或 HTTPS");
        }
        return value;
    }

    private URI parseAbsoluteUrl(String value, String propertyName) {
        try {
            var uri = URI.create(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(propertyName + " 必须是绝对 URL", exception);
        }
    }

    private void requireProductionUrl(String value, String propertyName) {
        var uri = parseAbsoluteUrl(value, propertyName);
        var lowerCaseUrl = value.toLowerCase(Locale.ROOT);
        var host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!uri.getScheme().equals("https")
                || host.equals("localhost")
                || host.endsWith(".localhost")
                || isLoopbackAddress(host)
                || lowerCaseUrl.contains("mock")) {
            throw new IllegalStateException(propertyName + " 在正式环境必须为非模拟 HTTPS 地址");
        }
    }

    private boolean isLoopbackAddress(String host) {
        var normalizedHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        if (normalizedHost.equals("127") || normalizedHost.startsWith("127.")) {
            return true;
        }
        if (!normalizedHost.contains(":")) {
            return false;
        }
        try {
            return InetAddress.getByName(normalizedHost).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private boolean contains(String value, String text) {
        return value != null && value.contains(text);
    }
}
