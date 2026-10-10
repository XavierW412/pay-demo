package com.startrail.payment.diagnostics.interfaces;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DiagnosticRequestReader {
    private final ObjectMapper mapper;
    public DiagnosticRequestReader(ObjectMapper mapper) { this.mapper = mapper; }

    public <T> T read(HttpServletRequest request, Set<String> fields, Class<T> type, int maxBytes) throws IOException {
        if (request.getQueryString() != null) reject("诊断上报不接受地址参数");
        if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请提交 JSON 数据");
        }
        if (request.getContentLengthLong() > maxBytes) tooLarge();
        byte[] bytes = request.getInputStream().readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) tooLarge();
        try (var parser = mapper.createParser(bytes)) {
            JsonNode root = mapper.readTree(parser);
            if (parser.nextToken() != null) reject("诊断上报只接受一个 JSON 对象");
            if (root == null || !root.isObject()) reject("诊断上报格式无效");
            for (String name : root.propertyNames()) {
                JsonNode value = root.get(name);
                if (!fields.contains(name) || (!value.isTextual() && !value.isNull())) {
                    reject("诊断上报包含不允许的字段或类型");
                }
            }
            return mapper.treeToValue(root, type);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "诊断上报格式无效");
        }
    }
    private void reject(String reason) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    private void tooLarge() { throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "诊断上报内容过大"); }
}
