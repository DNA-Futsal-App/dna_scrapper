package br.com.dnafutsal.scraper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "dna-futsal.api")
public record ApiAccessProperties(
        int requestsPerMinute,
        String allowedOrigins
) {

    public ApiAccessProperties {
        requestsPerMinute =
                requestsPerMinute <= 0
                        ? 60
                        : requestsPerMinute;

        allowedOrigins =
                allowedOrigins == null ||
                        allowedOrigins.isBlank()
                        ? "http://localhost:8080"
                        : allowedOrigins;
    }

    public List<String> allowedOriginList() {
        return Arrays.stream(
                        allowedOrigins.split(",")
                )
                .map(String::trim)
                .filter(value ->
                        !value.isBlank()
                )
                .toList();
    }
}