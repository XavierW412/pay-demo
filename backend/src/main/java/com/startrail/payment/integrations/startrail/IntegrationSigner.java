package com.startrail.payment.integrations.startrail;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSigner {
    public String sign(String timestamp, String nonce, String rawBody, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var signedContent = timestamp + "\n" + nonce + "\n" + rawBody;
            return HexFormat.of().formatHex(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成星辙集成请求签名", exception);
        }
    }
}
