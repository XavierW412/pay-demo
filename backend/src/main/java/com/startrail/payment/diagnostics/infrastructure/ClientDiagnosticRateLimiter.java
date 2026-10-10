package com.startrail.payment.diagnostics.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ClientDiagnosticRateLimiter {
    private final int perMinute;
    private final int globalPerMinute;
    private final int maxBuckets;
    private final Set<String> trustedProxies;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Integer> buckets = new HashMap<>();
    private final byte[] key = new byte[32];
    private long keyEpoch = -1;
    private long minute = -1;
    private int globalCount;

    public ClientDiagnosticRateLimiter(@Value("${diagnostics.client-events.per-minute:20}") int perMinute,
            @Value("${diagnostics.client-events.global-per-minute:300}") int globalPerMinute,
            @Value("${diagnostics.client-events.max-address-buckets:10000}") int maxBuckets,
            @Value("${diagnostics.client-events.trusted-proxy-addresses:}") String trustedProxies) {
        if (perMinute < 1 || globalPerMinute < 1 || maxBuckets < 1 || maxBuckets > 100000) {
            throw new IllegalArgumentException("诊断限流配置无效");
        }
        this.perMinute = perMinute;
        this.globalPerMinute = globalPerMinute;
        this.maxBuckets = maxBuckets;
        this.trustedProxies = Arrays.stream(trustedProxies.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public synchronized void acquire(HttpServletRequest request) {
        long now = System.currentTimeMillis() / 1000;
        long currentEpoch = now / 3600;
        if (keyEpoch != currentEpoch) {
            random.nextBytes(key);
            keyEpoch = currentEpoch;
            buckets.clear();
        }
        if (minute != now / 60) {
            minute = now / 60;
            buckets.clear();
            globalCount = 0;
        }
        if (globalCount >= globalPerMinute) limited();
        String bucket = hash(address(request));
        if (!buckets.containsKey(bucket) && buckets.size() >= maxBuckets) limited();
        int count = buckets.getOrDefault(bucket, 0);
        if (count >= perMinute) limited();
        buckets.put(bucket, count + 1);
        globalCount++;
    }

    private String address(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        // 默认只认连接地址。显式列入可信代理后，仅接受代理覆盖生成的单一数值地址，不解析任意转发链。
        if (trustedProxies.contains(remoteAddress)) {
            var headers = request.getHeaders("X-Forwarded-For");
            String forwarded = headers.hasMoreElements() ? headers.nextElement() : null;
            if (headers.hasMoreElements()) return remoteAddress;
            if (forwarded != null && forwarded.length() <= 45 && (forwarded.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")
                    || (forwarded.contains(":") && forwarded.matches("[0-9a-fA-F:.]{2,45}")))) {
                try {
                    return java.net.InetAddress.getByName(forwarded).getHostAddress();
                } catch (java.net.UnknownHostException ignored) {
                    // 无效或包含地址链的头按连接地址限流。
                }
            }
        }
        return remoteAddress == null ? "unknown" : remoteAddress;
    }
    private String hash(String address) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(address.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("诊断限流算法不可用");
        }
    }
    private void limited() { throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "诊断上报过于频繁，请稍后再试"); }
}
