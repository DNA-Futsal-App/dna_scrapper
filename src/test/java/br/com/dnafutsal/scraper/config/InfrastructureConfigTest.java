package br.com.dnafutsal.scraper.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureConfigTest {

    @Test
    void createsSmallShortLivedSourcePageCache() {
        InfrastructureConfig configuration =
                new InfrastructureConfig();

        SimpleCacheManager manager =
                (SimpleCacheManager)
                        configuration
                                .cacheManager();

        manager.initializeCaches();

        CaffeineCache springCache =
                (CaffeineCache)
                        manager.getCache(
                                "source-page"
                        );

        assertThat(
                springCache
        ).isNotNull();

        Cache<Object, Object> cache =
                springCache
                        .getNativeCache();

        assertThat(
                cache.policy()
                        .eviction()
        ).isPresent();

        assertThat(
                cache.policy()
                        .eviction()
                        .orElseThrow()
                        .getMaximum()
        ).isEqualTo(300);

        assertThat(
                cache.policy()
                        .expireAfterWrite()
        ).isPresent();

        assertThat(
                cache.policy()
                        .expireAfterWrite()
                        .orElseThrow()
                        .getExpiresAfter()
        ).isEqualTo(
                Duration.ofMinutes(3)
        );
    }

    @Test
    void cacheReusesLoadedValue() {
        InfrastructureConfig configuration =
                new InfrastructureConfig();

        SimpleCacheManager manager =
                (SimpleCacheManager)
                        configuration
                                .cacheManager();

        manager.initializeCaches();

        var cache =
                manager.getCache(
                        "source-page"
                );

        int[] loads = {0};

        String first =
                cache.get(
                        "base:917",
                        () -> {
                            loads[0]++;
                            return "<html>first</html>";
                        }
                );

        String second =
                cache.get(
                        "base:917",
                        () -> {
                            loads[0]++;
                            return "<html>second</html>";
                        }
                );

        assertThat(first)
                .isEqualTo(
                        "<html>first</html>"
                );

        assertThat(second)
                .isEqualTo(
                        "<html>first</html>"
                );

        assertThat(loads[0])
                .isEqualTo(1);
    }
}