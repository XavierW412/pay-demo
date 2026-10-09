package com.startrail.payment.integrations.airwallex.auth;

import com.startrail.payment.integrations.airwallex.config.AirwallexProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Service
public class AirwallexAccessTokenService {
    private final RestClient restClient;
    private final AirwallexProperties properties;
    private CachedAccessToken cachedAccessToken;

    public AirwallexAccessTokenService(
            RestClient.Builder restClientBuilder,
            AirwallexProperties properties,
            ClientHttpRequestFactory airwallexRequestFactory) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl())
                .requestFactory(airwallexRequestFactory).build();
        this.properties = properties;
    }

    public synchronized String getAccessToken() {
        if (cachedAccessToken != null && cachedAccessToken.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cachedAccessToken.token();
        }
        if (isBlank(properties.clientId()) || isBlank(properties.apiKey())) {
            throw new AirwallexConfigurationException("未配置 Airwallex 沙盒 Client ID 和 API Key");
        }

        var response = restClient.post()
                .uri("/authentication/login")
                .header("x-client-id", properties.clientId())
                .header("x-api-key", properties.apiKey())
                .retrieve()
                .body(AccessTokenResponse.class);
        if (response == null || isBlank(response.token()) || isBlank(response.expiresAt())) {
            throw new AirwallexIntegrationException("Airwallex 登录响应缺少访问令牌或过期时间");
        }

        cachedAccessToken = new CachedAccessToken(response.token(), parseExpiration(response.expiresAt()));
        return cachedAccessToken.token();
    }

    private Instant parseExpiration(String expiresAt) {
        try {
            var normalized = expiresAt.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (RuntimeException exception) {
            throw new AirwallexIntegrationException("Airwallex 登录响应过期时间格式无效");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedAccessToken(String token, Instant expiresAt) {
    }

    private record AccessTokenResponse(
            String token,
            @JsonProperty("expires_at") String expiresAt) {
    }
}
