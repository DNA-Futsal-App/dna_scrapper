package br.com.dnafutsal.scraper.http;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.exception.ResourceNotFoundException;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PoliteHttpFetcher {

    private final HttpClient client;
    private final ScraperProperties properties;
    private final RobotsGuard robotsGuard;
    private final Semaphore singleUpstreamCall = new Semaphore(1, true);
    private final AtomicLong nextAllowedRequestAt = new AtomicLong(0L);
    private final Set<String> allowedHosts;

    public PoliteHttpFetcher(HttpClient upstreamHttpClient, ScraperProperties properties, RobotsGuard robotsGuard) {
        this.client = upstreamHttpClient;
        this.properties = properties;
        this.robotsGuard = robotsGuard;
        Set<String> hosts = new HashSet<>();
        hosts.add(URI.create(properties.baseUrl()).getHost().toLowerCase(Locale.ROOT));
        hosts.add(URI.create(properties.matchSheetBaseUrl()).getHost().toLowerCase(Locale.ROOT));
        this.allowedHosts = Set.copyOf(hosts);
    }

    public Document getDocument(String url) {
        String html = getText(url);
        return Jsoup.parse(html, url);
    }

    public String getText(String url) {
        URI uri = validatedUri(url);
        robotsGuard.assertAllowed(uri);
        int attempt = 0;
        while (true) {
            boolean acquired = false;
            try {
                singleUpstreamCall.acquire();
                acquired = true;
                waitForPoliteInterval();
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(properties.requestTimeout())
                        .header("User-Agent", properties.userAgent())
                        .header("Accept", "text/html,application/xhtml+xml,application/pdf;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "pt-BR,pt;q=0.9")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                markNextAllowedRequest();

                if (response.statusCode() == 404) {
                    throw new ResourceNotFoundException("Recurso não encontrado no site de origem: " + uri.getPath());
                }
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                if (isRetryable(response.statusCode()) && attempt < properties.maxRetries()) {
                    sleep(backoff(attempt++, response));
                    continue;
                }
                throw new UpstreamAccessException(
                        "O site de origem respondeu HTTP " + response.statusCode() + " para " + uri.getPath()
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new UpstreamAccessException("A consulta ao site de origem foi interrompida", exception);
            } catch (IOException exception) {
                if (attempt < properties.maxRetries()) {
                    sleep(backoff(attempt++, null));
                    continue;
                }
                throw new UpstreamAccessException("Falha de rede ao consultar o site de origem", exception);
            } finally {
                if (acquired) {
                    singleUpstreamCall.release();
                }
            }
        }
    }

    private URI validatedUri(String url) {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("URL fora dos hosts permitidos para scraping");
        }
        return uri;
    }

    private void waitForPoliteInterval() throws InterruptedException {
        long remainingNanos = nextAllowedRequestAt.get() - System.nanoTime();
        if (remainingNanos > 0) {
            long millis = Duration.ofNanos(remainingNanos).toMillis();
            Thread.sleep(Math.max(millis, 1L));
        }
    }

    private void markNextAllowedRequest() {
        nextAllowedRequestAt.set(System.nanoTime() + properties.minimumDelay().toNanos());
    }

    private boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private Duration backoff(int attempt, HttpResponse<?> response) {
        if (response != null) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            if (retryAfter != null && retryAfter.matches("\\d+")) {
                return Duration.ofSeconds(Math.min(Long.parseLong(retryAfter), 30));
            }
        }
        return Duration.ofMillis(Math.min(750L * (1L << attempt), 5_000L));
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UpstreamAccessException("A espera entre tentativas foi interrompida", exception);
        }
    }
}
