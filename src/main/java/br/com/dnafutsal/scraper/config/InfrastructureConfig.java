package br.com.dnafutsal.scraper.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.concurrent.TimeUnit;

@Configuration
public class InfrastructureConfig {

    @Bean
    HttpClient upstreamHttpClient(ScraperProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "event-metadata", "standings", "games", "teams", "team-details", "scorers", "event-search", "snapshot"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }
}
