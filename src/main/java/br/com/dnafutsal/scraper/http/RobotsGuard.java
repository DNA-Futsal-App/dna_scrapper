package br.com.dnafutsal.scraper.http;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RobotsGuard {

    private final HttpClient client;
    private final ScraperProperties properties;
    private final ConcurrentMap<String, List<String>> disallowedPrefixesByHost = new ConcurrentHashMap<>();

    public RobotsGuard(HttpClient upstreamHttpClient, ScraperProperties properties) {
        this.client = upstreamHttpClient;
        this.properties = properties;
    }

    public void assertAllowed(URI target) {
        if (!properties.respectRobotsTxt() || "/robots.txt".equals(target.getPath())) {
            return;
        }
        List<String> disallowed = disallowedPrefixesByHost.computeIfAbsent(target.getHost(), this::loadRules);
        String path = target.getPath() == null || target.getPath().isBlank() ? "/" : target.getPath();
        for (String prefix : disallowed) {
            if (!prefix.isBlank() && path.startsWith(prefix)) {
                throw new UpstreamAccessException("Acesso bloqueado pelo robots.txt para o caminho " + path);
            }
        }
    }

    private List<String> loadRules(String host) {
        try {
            URI robotsUri = URI.create("https://" + host + "/robots.txt");
            HttpRequest request = HttpRequest.newBuilder(robotsUri)
                    .timeout(properties.requestTimeout())
                    .header("User-Agent", properties.userAgent())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return List.of();
            }
            return parseWildcardRules(response.body());
        } catch (Exception ignored) {
            // Falha ao obter robots.txt não vira permissão para alta carga: o rate limit continua ativo.
            return List.of();
        }
    }

    private List<String> parseWildcardRules(String body) {
        List<String> result = new ArrayList<>();
        boolean applies = false;
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            if (line.isBlank()) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            if (key.equals("user-agent")) {
                applies = value.equals("*") || properties.userAgent().toLowerCase(Locale.ROOT)
                        .startsWith(value.toLowerCase(Locale.ROOT));
            } else if (applies && key.equals("disallow") && !value.isBlank()) {
                // Implementação conservadora de prefixos. Regras complexas com curingas devem ser validadas manualmente.
                result.add(value.replace("*", ""));
            }
        }
        return List.copyOf(result);
    }
}
