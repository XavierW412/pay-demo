package com.startrail.payment.diagnostics.application;

import java.text.Normalizer;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DiagnosticTextSanitizer {
    private DiagnosticTextSanitizer() {}

    public static String sanitize(String text, int maxLength) {
        if (text == null) return null;
        if (text.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "诊断文字内容过长");
        }
        if (text.isBlank()) return null;
        // 自由文本不保留网址、认证片段、ASCII 标识或数字，避免业务单号和付款资料进入摘要。
        String sanitized = Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("(?i)https?://[^\\s]+", "[已隐藏地址]")
                .replaceAll("(?i)(密码|密钥|令牌|卡号|姓名|邮箱|电话|地址|cookie|authorization|secret|password|token)[\\s:：=]*[^\\s，。；;]*", "[已隐藏敏感内容]")
                .replaceAll("[A-Za-z\\p{N}_@./:+=%#?&\\-]+", "[已隐藏标识]")
                .replaceAll("[\\p{Cntrl}<>]", " ").trim();
        if (sanitized.isBlank()) return null;
        if (sanitized.length() > maxLength) {
            int end = Character.isHighSurrogate(sanitized.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
            return sanitized.substring(0, end);
        }
        return sanitized;
    }
}
