package br.com.dnafutsal.scraper.api;

import br.com.dnafutsal.scraper.config.ApiAccessProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
public class ApiAccessFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final ApiAccessProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ApiAccessFilter(ApiAccessProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String configuredKey = properties.apiKey();
        String suppliedKey = request.getHeader("X-API-Key");

        if (!configuredKey.isBlank() && !constantTimeEquals(configuredKey, suppliedKey)) {
            writeProblem(response, 401, "Não autorizado", "Informe uma chave válida no cabeçalho X-API-Key");
            return;
        }

        String clientKey = configuredKey.isBlank() ? request.getRemoteAddr() : configuredKey;
        if (!consume(clientKey)) {
            response.setHeader("Retry-After", "60");
            writeProblem(response, 429, "Limite excedido", "Limite de requisições por minuto excedido");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean consume(String clientKey) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(clientKey, (key, current) -> {
            if (current == null || now - current.startedAtMillis() >= WINDOW_MILLIS) {
                return new Window(now, new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtMillis() >= WINDOW_MILLIS);
        }
        return window.count().get() <= properties.requestsPerMinute();
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void writeProblem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "status", status,
                "title", title,
                "detail", detail,
                "timestamp", Instant.now().toString()
        ));
    }

    private record Window(long startedAtMillis, AtomicInteger count) {
    }
}
