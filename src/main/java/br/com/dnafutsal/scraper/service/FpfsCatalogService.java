package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.api.CatalogOption;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.domain.SeasonCatalog;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import br.com.dnafutsal.scraper.fpfs.FpfsCatalogClient;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsCategoryEventResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsDivisionResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsTitleResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FpfsCatalogService {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private final FpfsCatalogLoader loader;
    private final FpfsCatalogClient client;

    public FpfsCatalogService(
            FpfsCatalogLoader loader,
            FpfsCatalogClient client
    ) {
        this.loader = loader;
        this.client = client;
    }

    public List<CatalogOption> divisions(
            int season
    ) {
        SeasonCatalog catalog =
                loader.loadPaulista(season);

        return catalog.divisions()
                .stream()
                .map(division ->
                        new CatalogOption(
                                division.id(),
                                division.name()
                        )
                )
                .toList();
    }

    public List<CatalogOption> categories(
            int season,
            String division
    ) {
        SeasonCatalog catalog =
                loader.loadPaulista(season);

        SeasonCatalog.Division selected =
                findDivision(
                        catalog,
                        division
                );

        return selected.categories()
                .stream()
                .map(category ->
                        new CatalogOption(
                                category.id(),
                                category.name()
                        )
                )
                .toList();
    }

    private SeasonCatalog.Division findDivision(
            SeasonCatalog catalog,
            String requested
    ) {
        String normalized =
                normalize(requested);

        return catalog.divisions()
                .stream()
                .filter(division ->
                        normalize(division.name())
                                .equals(normalized)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Divisão não encontrada: "
                                        + requested
                        )
                );
    }

    private String normalize(
            String value
    ) {
        if (value == null) {
            return "";
        }

        String normalized =
                Normalizer.normalize(
                        value.trim(),
                        Normalizer.Form.NFD
                );

        return DIACRITICS
                .matcher(normalized)
                .replaceAll("")
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    public List<Long> searchEventIds(
            EventSearchCriteria criteria
    ) {
        if (criteria.season() == null) {
            throw new IllegalArgumentException(
                    "A temporada é obrigatória"
            );
        }

        List<FpfsTitleResponse> titles =
                activeTitles(
                        criteria.season(),
                        criteria.title()
                );

        Set<Long> eventIds =
                new LinkedHashSet<>();

        for (FpfsTitleResponse title : titles) {

            List<FpfsDivisionResponse> divisions =
                    activeDivisions(
                            criteria.season(),
                            title.id(),
                            criteria.division()
                    );

            for (FpfsDivisionResponse division :
                    divisions) {

                List<FpfsCategoryEventResponse> events =
                        client.categoryEvents(
                                criteria.season(),
                                title.id(),
                                division.id()
                        );

                for (FpfsCategoryEventResponse event :
                        events) {

                    if (!active(event.status())) {
                        continue;
                    }

                    if (event.category() == null) {
                        continue;
                    }

                    if (!active(
                            event.category().status()
                    )) {
                        continue;
                    }

                    if (hasText(criteria.category())
                            && !sameName(
                            event.category().name(),
                            criteria.category()
                    )) {

                        continue;
                    }

                    eventIds.add(
                            event.eventId()
                    );
                }
            }
        }

        return List.copyOf(eventIds);
    }

    private List<FpfsTitleResponse> activeTitles(
            int season,
            String requestedTitle
    ) {
        List<FpfsTitleResponse> active =
                client.titles(season)
                        .stream()
                        .filter(title ->
                                active(title.status())
                        )
                        .toList();

        if (!hasText(requestedTitle)) {
            return active;
        }

        String requested =
                normalizeTitle(
                        requestedTitle
                );

        /*
         * Primeiro tenta match exato.
         *
         * Isso evita:
         *
         * Paulista
         *
         * casar com:
         *
         * Copa Paulista
         * Paulista Feminino
         */
        List<FpfsTitleResponse> exact =
                active.stream()
                        .filter(title ->
                                normalizeTitle(
                                        title.name()
                                ).equals(requested)
                        )
                        .toList();

        if (!exact.isEmpty()) {
            return exact;
        }

        return active.stream()
                .filter(title ->
                        normalizeTitle(
                                title.name()
                        ).contains(requested)
                )
                .toList();
    }

    private List<FpfsDivisionResponse> activeDivisions(
            int season,
            long titleId,
            String requestedDivision
    ) {
        List<FpfsDivisionResponse> active =
                client.divisions(
                                season,
                                titleId
                        )
                        .stream()
                        .filter(division ->
                                active(division.status())
                        )
                        .toList();

        if (!hasText(requestedDivision)) {
            return active;
        }

        return active.stream()
                .filter(division ->
                        sameName(
                                division.name(),
                                requestedDivision
                        )
                )
                .toList();
    }

    private boolean active(
            String status
    ) {
        return status == null
                || status.isBlank()
                || "A".equalsIgnoreCase(status);
    }

    private boolean sameName(
            String first,
            String second
    ) {
        return normalize(first)
                .equals(
                        normalize(second)
                );
    }

    private String normalizeTitle(
            String value
    ) {
        String normalized =
                normalize(value);

        if (normalized.startsWith(
                "campeonato "
        )) {
            normalized =
                    normalized.substring(
                            "campeonato ".length()
                    );
        }

        return normalized;
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}