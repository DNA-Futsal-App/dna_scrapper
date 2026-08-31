package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.domain.SeasonCatalog;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import br.com.dnafutsal.scraper.fpfs.FpfsCatalogClient;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsCategoryEventResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsCategoryResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsDivisionResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsTitleResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class FpfsCatalogLoader {

    public static final String CACHE_NAME =
            "fpfs-catalog";

    private static final String PAULISTA =
            "Paulista";

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private final FpfsCatalogClient client;

    public FpfsCatalogLoader(
            FpfsCatalogClient client
    ) {
        this.client = client;
    }

    @Cacheable(
            cacheNames = CACHE_NAME,
            key = "#season",
            sync = true
    )
    public SeasonCatalog loadPaulista(
            int season
    ) {
        FpfsTitleResponse paulista =
                findPaulista(season);

        List<FpfsDivisionResponse> fpfsDivisions =
                client.divisions(
                        season,
                        paulista.id()
                );

        List<SeasonCatalog.Division> divisions =
                new ArrayList<>();

        for (
                FpfsDivisionResponse division :
                fpfsDivisions
        ) {
            if (
                    division == null
                            || division.id() <= 0
                            || !hasText(
                            division.name()
                    )
                            || !isActive(
                            division.status()
                    )
            ) {
                continue;
            }

            List<FpfsCategoryEventResponse> events =
                    client.categoryEvents(
                            season,
                            paulista.id(),
                            division.id()
                    );

            Map<Long, SeasonCatalog.Category> categories =
                    new LinkedHashMap<>();

            for (
                    FpfsCategoryEventResponse event :
                    events
            ) {
                if (
                        !isUsableEvent(
                                event,
                                season,
                                paulista.id(),
                                division.id()
                        )
                ) {
                    continue;
                }

                FpfsCategoryResponse category =
                        event.category();

                int order =
                        category.executionOrder()
                                == null
                                ? Integer.MAX_VALUE
                                : category.executionOrder();

                categories.putIfAbsent(
                        category.id(),
                        new SeasonCatalog.Category(
                                category.id(),
                                clean(
                                        category.name()
                                ),
                                order,
                                event.eventId()
                        )
                );
            }

            List<SeasonCatalog.Category> ordered =
                    categories.values()
                            .stream()
                            .sorted(
                                    Comparator
                                            .comparingInt(
                                                    SeasonCatalog.Category::order
                                            )
                                            .thenComparing(
                                                    SeasonCatalog.Category::name,
                                                    String.CASE_INSENSITIVE_ORDER
                                            )
                            )
                            .toList();

            /*
             * Uma divisão sem nenhum evento válido
             * não é selecionável pelo produto.
             */
            if (ordered.isEmpty()) {
                continue;
            }

            divisions.add(
                    new SeasonCatalog.Division(
                            division.id(),
                            clean(
                                    division.name()
                            ),
                            ordered
                    )
            );
        }

        divisions.sort(
                Comparator.comparing(
                        SeasonCatalog.Division::name,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        return new SeasonCatalog(
                season,
                paulista.id(),
                clean(
                        paulista.name()
                ),
                List.copyOf(
                        divisions
                )
        );
    }

    private FpfsTitleResponse findPaulista(
            int season
    ) {
        return client.titles(season)
                .stream()
                .filter(title ->
                        title != null
                                && title.id() > 0
                                && hasText(
                                title.name()
                        )
                )
                .filter(title ->
                        isActive(
                                title.status()
                        )
                )
                .filter(title ->
                        normalize(
                                title.name()
                        ).equals(
                                normalize(
                                        PAULISTA
                                )
                        )
                )
                .findFirst()
                .orElseThrow(() ->
                        new UpstreamAccessException(
                                "O campeonato Paulista não foi encontrado na FPFS para a temporada "
                                        + season
                        )
                );
    }

    private boolean isUsableEvent(
            FpfsCategoryEventResponse event,
            int season,
            long titleId,
            long divisionId
    ) {
        if (
                event == null
                        || event.eventId() <= 0
                        || event.titleId() != titleId
                        || event.divisionId() != divisionId
                        || event.season() != season
                        || !isActive(
                        event.status()
                )
        ) {
            return false;
        }

        FpfsCategoryResponse category =
                event.category();

        return category != null
                && category.id() > 0
                && event.categoryId()
                == category.id()
                && hasText(
                category.name()
        )
                && isActive(
                category.status()
        );
    }

    private boolean isActive(
            String status
    ) {
        return status == null
                || status.isBlank()
                || "A".equalsIgnoreCase(
                status
        );
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private String clean(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private String normalize(
            String value
    ) {
        String normalized =
                Normalizer.normalize(
                        clean(value),
                        Normalizer.Form.NFD
                );

        return DIACRITICS
                .matcher(
                        normalized
                )
                .replaceAll("")
                .toLowerCase(
                        Locale.ROOT
                );
    }
}