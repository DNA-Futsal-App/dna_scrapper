package br.com.dnafutsal.scraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "dna-futsal.scraper")
public record ScraperProperties(
        String baseUrl,
        String matchSheetBaseUrl,
        String userAgent,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration minimumDelay,
        int maxRetries,
        boolean browserSearchEnabled,
        boolean exposePersonalData,
        boolean respectRobotsTxt
) {
    public ScraperProperties {
        baseUrl = normalizeHttpsBaseUrl(defaultIfBlank(baseUrl, "https://eventos.admfutsal.com.br"));
        matchSheetBaseUrl = normalizeHttpsBaseUrl(defaultIfBlank(matchSheetBaseUrl, "https://admfutsal.com.br"));
        userAgent = defaultIfBlank(userAgent, "DNAFutsalDataAdapter/1.0 (+contato@seu-dominio.com.br)");
        connectTimeout = positiveOrDefault(connectTimeout, Duration.ofSeconds(8));
        requestTimeout = positiveOrDefault(requestTimeout, Duration.ofSeconds(20));
        minimumDelay = minimumDelay == null || minimumDelay.compareTo(Duration.ofMillis(250)) < 0
                ? Duration.ofMillis(900)
                : minimumDelay;
        maxRetries = Math.max(0, Math.min(maxRetries, 5));
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static String normalizeHttpsBaseUrl(String value) {
        String normalized = value.trim().replaceAll("/+$", "");
        URI uri = URI.create(normalized);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("As URLs do scraper devem usar HTTPS e possuir um host válido");
        }
        return normalized;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
