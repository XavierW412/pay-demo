package com.startrail.payment.observability.infrastructure;

import com.startrail.payment.shared.interfaces.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private final ObjectMapper objectMapper;

    public RequestCorrelationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        try {
            response.setHeader("X-Request-Id", requestId);
            putMdc("requestId", requestId);
        } catch (RuntimeException ignored) {
            // 请求日志上下文建立失败时仍继续支付请求。
        }
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            boolean handled;
            try {
                handled = handleUnhandledFailure(response, requestId, exception);
            } catch (RuntimeException ignored) {
                handled = false;
            }
            if (!handled) {
                throw exception;
            }
        } finally {
            try {
                writeRequestSummary(request, response);
            } finally {
                removeMdc("httpStatus");
                removeMdc("routeTemplate");
                removeMdc("requestId");
            }
        }
    }

    private boolean handleUnhandledFailure(HttpServletResponse response, String requestId, Exception exception) {
        try {
            putMdc("exceptionClass", exception.getClass().getSimpleName());
            putMdc("stackFrames", SafeStackFrames.summarize(exception));
            putMdc("httpStatus", Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            safeLog(true);
        } finally {
            removeMdc("exceptionClass");
            removeMdc("stackFrames");
            removeMdc("httpStatus");
        }
        try {
            if (response.isCommitted()) {
                return false;
            }
            response.resetBuffer();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setHeader("X-Request-Id", requestId);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse("INTERNAL_ERROR",
                    "服务暂时不可用，请稍后重试", Instant.now()));
            return true;
        } catch (IOException | RuntimeException ignored) {
            // 响应已提交或连接中断时，保留原始异常供容器处理。
            return false;
        }
    }

    private void writeRequestSummary(HttpServletRequest request, HttpServletResponse response) {
        try {
            Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (matchedPattern instanceof String pattern && pattern.matches("[A-Za-z0-9/_{}.*-]{1,160}")) {
                putMdc("routeTemplate", pattern);
            }
            putMdc("httpStatus", Integer.toString(response.getStatus()));
            safeLog(false);
        } catch (RuntimeException ignored) {
            // 路由属性或日志组件故障不影响请求处理。
        }
    }

    private void safeLog(boolean failure) {
        try {
            if (failure) {
                LOGGER.error("请求处理失败");
            } else {
                LOGGER.info("请求完成");
            }
        } catch (RuntimeException ignored) {
            // 运行日志故障不影响支付请求。
        }
    }

    private void putMdc(String key, String value) {
        try {
            MDC.put(key, value);
        } catch (RuntimeException ignored) {
            // 请求日志上下文不可用时仍继续处理请求。
        }
    }

    private void removeMdc(String key) {
        try {
            MDC.remove(key);
        } catch (RuntimeException ignored) {
            // 不能让诊断组件清理失败覆盖请求结果。
        }
    }
}
