package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.http.PoliteHttpFetcher;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class EventSourceService {

    private static final Logger log =
            LoggerFactory.getLogger(EventSourceService.class);

    private final ScraperProperties properties;
    private final PoliteHttpFetcher fetcher;

    public EventSourceService(
            ScraperProperties properties,
            PoliteHttpFetcher fetcher
    ) {
        this.properties = properties;
        this.fetcher = fetcher;
    }

    @Cacheable(
            cacheNames = "source-page",
            key = "'base:' + #eventId",
            sync = true
    )
    public Page basePage(long eventId) {
        return load(eventId, "");
    }

    @Cacheable(
            cacheNames = "source-page",
            key = "'games:' + #eventId",
            sync = true
    )
    public Page gamesPage(long eventId) {
        return load(eventId, "/jogos");
    }

    @Cacheable(
            cacheNames = "source-page",
            key = "'teams:' + #eventId",
            sync = true
    )
    public Page teamsPage(long eventId) {
        return load(eventId, "/equipes");
    }

    public Page scorersPage(long eventId) {
        return load(eventId, "/artilharia");
    }

    public Page teamDetailsPage(
            long eventId,
            long teamId
    ) {
        return load(
                eventId,
                "/equipe/" + teamId
        );
    }

    private Page load(
            long eventId,
            String suffix
    ) {
        String url =
                properties.baseUrl()
                        + "/evento/"
                        + eventId
                        + suffix;

        log.debug(
                "Loading FPFS page eventId={} suffix={}",
                eventId,
                suffix.isBlank()
                        ? "/"
                        : suffix
        );

        return new Page(
                url,
                fetcher.getText(url)
        );
    }

    public record Page(
            String url,
            String html
    ) {
        public Document document() {
            return Jsoup.parse(
                    html,
                    url
            );
        }
    }
}