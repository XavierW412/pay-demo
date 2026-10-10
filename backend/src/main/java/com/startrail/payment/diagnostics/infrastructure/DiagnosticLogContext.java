package com.startrail.payment.diagnostics.infrastructure;

import com.startrail.payment.diagnostics.application.DiagnosticStackFrames;
import org.slf4j.Logger;
import org.slf4j.MDC;

/** 诊断日志只作辅助记录，不能改变业务请求或错误响应。 */
public final class DiagnosticLogContext {
    private DiagnosticLogContext() {}

    public static void record(Logger logger, String diagnosticId, String exceptionClass, int httpStatus,
            Throwable exception) {
        try {
            putSafely("diagnosticId", diagnosticId);
            putSafely("exceptionClass", exceptionClass);
            putSafely("httpStatus", Integer.toString(httpStatus));
            if (httpStatus >= 500 && exception != null) {
                putSafely("stackFrames", DiagnosticStackFrames.summarize(exception));
            }
            try {
                if (httpStatus >= 500) {
                    logger.error("请求处理失败");
                } else {
                    logger.info("请求被拒绝");
                }
            } catch (RuntimeException ignored) {
                // 日志后端故障不能影响业务请求。
            }
        } catch (RuntimeException ignored) {
            // 栈帧摘要等诊断数据读取失败时仍需完成原响应。
        } finally {
            removeSafely("diagnosticId");
            removeSafely("exceptionClass");
            removeSafely("httpStatus");
            removeSafely("stackFrames");
        }
    }

    private static void putSafely(String key, String value) {
        try {
            MDC.put(key, value);
        } catch (RuntimeException ignored) {
            // 单个字段写入失败不能影响其他字段或业务请求。
        }
    }

    private static void removeSafely(String key) {
        try {
            MDC.remove(key);
        } catch (RuntimeException ignored) {
            // 始终继续尝试清理剩余字段。
        }
    }
}
