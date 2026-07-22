package br.com.dnafutsal.scraper;

import br.com.dnafutsal.scraper.config.ApiAccessProperties;
import br.com.dnafutsal.scraper.config.ScraperProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@EnableConfigurationProperties({ScraperProperties.class, ApiAccessProperties.class})
@SpringBootApplication
public class DnaFutsalScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(DnaFutsalScraperApplication.class, args);
    }
}
