package com.example.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 簡易的なAPIキー認証フィルター。/api/sync/ 配下にアクセスされた際に
 * X-API-Key ヘッダーの値を検証し、設定値と一致しない場合は401を返す。
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String SYNC_ENDPOINT_PREFIX = "/api/sync/";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyAuthFilter(@Value("${SECURITY_SYNC_API_KEY:}") String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!requiresProtection(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(expectedApiKey)) {
            respondWithError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SECURITY_SYNC_API_KEY is not configured");
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (!expectedApiKey.equals(providedKey)) {
            respondWithError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or invalid API key");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresProtection(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith(SYNC_ENDPOINT_PREFIX);
    }

    private void respondWithError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
