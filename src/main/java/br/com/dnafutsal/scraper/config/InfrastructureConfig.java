package br.com.dnafutsal.scraper.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

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
        SimpleCacheManager manager =
                new SimpleCacheManager();

        manager.setCaches(
                List.of(
                        /*
                         * HTML bruto:
                         * cache curto e menor para
                         * controlar consumo de memória.
                         */
                        cache(
                                "source-page",
                                300,
                                Duration.ofMinutes(3)
                        ),

                        cache(
                                "event-metadata",
                                2_000,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "standings",
                                2_000,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "games",
                                2_000,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "teams",
                                2_000,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "team-details",
                                1_000,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "scorers",
                                1_000,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "event-search",
                                500,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "snapshot",
                                500,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "fpfs-catalog",
                                100,
                                Duration.ofMinutes(30)
                        ),

                        /*
                         * Mantenha estes três apenas se
                         * ainda houver @Cacheable usando-os.
                         */
                        cache(
                                "divisions",
                                500,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "categories",
                                500,
                                Duration.ofMinutes(10)
                        ),

                        cache(
                                "category-teams",
                                500,
                                Duration.ofMinutes(10)
                        )
                )
        );

        return manager;
    }

    private CaffeineCache cache(
            String name,
            long maximumSize,
            Duration ttl
    ) {
        return new CaffeineCache(
                name,
                Caffeine.newBuilder()
                        .maximumSize(
                                maximumSize
                        )
                        .expireAfterWrite(
                                ttl
                        )
                        .recordStats()
                        .build(),
                false
        );
    }
}
