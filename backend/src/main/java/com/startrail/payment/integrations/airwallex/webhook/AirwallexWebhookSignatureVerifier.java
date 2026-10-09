package com.startrail.payment.integrations.airwallex.webhook;

import com.startrail.payment.integrations.airwallex.auth.AirwallexConfigurationException;
import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AirwallexWebhookSignatureVerifier {
    private static final Duration MAX_TIMESTAMP_DRIFT = Duration.ofMinutes(5);
    private final AirwallexProperties properties;

    public AirwallexWebhookSignatureVerifier(AirwallexProperties properties) {
        this.properties = properties;
    }

    public void verify(String timestamp, String signature, byte[] rawBody) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new AirwallexConfigurationException("尚未配置 Airwallex Webhook 密钥");
        }
        final Instant signedAt;
        try {
            signedAt = Instant.ofEpochMilli(Long.parseLong(timestamp));
        } catch (RuntimeException exception) {
            throw new InvalidAirwallexWebhookException("Airwallex Webhook 时间戳无效");
        }
        if (Duration.between(signedAt, Instant.now()).abs().compareTo(MAX_TIMESTAMP_DRIFT) > 0) {
            throw new InvalidAirwallexWebhookException("Airwallex Webhook 时间戳已过期");
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            var expected = mac.doFinal(rawBody);
            final byte[] supplied;
            try {
                supplied = HexFormat.of().parseHex(signature);
            } catch (RuntimeException exception) {
                throw new InvalidAirwallexWebhookException("Airwallex Webhook 签名格式无效");
            }
            if (!MessageDigest.isEqual(expected, supplied)) {
                throw new InvalidAirwallexWebhookException("Airwallex Webhook 签名校验失败");
            }
        } catch (InvalidAirwallexWebhookException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("无法校验 Airwallex Webhook 签名", exception);
        }
    }
}
