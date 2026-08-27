package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.api.CatalogCategoryOption;
import br.com.dnafutsal.scraper.api.CatalogOption;
import br.com.dnafutsal.scraper.domain.EventSearchCriteria;
import br.com.dnafutsal.scraper.domain.SeasonCatalog;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class FpfsCatalogService {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private final FpfsCatalogLoader loader;

    public FpfsCatalogService(
            FpfsCatalogLoader loader
    ) {
        this.loader = loader;
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

    public List<CatalogCategoryOption> categories(
            int season,
            long divisionId
    ) {
        SeasonCatalog catalog =
                loader.loadPaulista(season);

        SeasonCatalog.Division division =
                findDivision(
                        catalog,
                        divisionId
                );

        return division.categories()
                .stream()
                .map(category ->
                        new CatalogCategoryOption(
                                category.id(),
                                category.name(),
                                category.eventId()
                        )
                )
                .toList();
    }

    public List<Long> searchEventIds(
            EventSearchCriteria criteria
    ) {
        if (criteria.season() == null) {
            throw new IllegalArgumentException(
                    "A temporada é obrigatória"
            );
        }

        SeasonCatalog catalog =
                loader.loadPaulista(
                        criteria.season()
                );

        if (
                hasText(criteria.title()) &&
                        !sameTitle(
                                catalog.titleName(),
                                criteria.title()
                        )
        ) {
            return List.of();
        }

        return catalog.divisions()
                .stream()
                .filter(division ->
                        !hasText(criteria.division())
                                || sameName(
                                division.name(),
                                criteria.division()
                        )
                )
                .flatMap(division ->
                        division.categories()
                                .stream()
                )
                .filter(category ->
                        !hasText(criteria.category())
                                || sameName(
                                category.name(),
                                criteria.category()
                        )
                )
                .map(
                        SeasonCatalog.Category::eventId
                )
                .distinct()
                .toList();
    }

    private SeasonCatalog.Division findDivision(
            SeasonCatalog catalog,
            long divisionId
    ) {
        return catalog.divisions()
                .stream()
                .filter(division ->
                        division.id() == divisionId
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Divisão não encontrada: "
                                        + divisionId
                        )
                );
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

    private boolean sameTitle(
            String first,
            String second
    ) {
        return normalizeTitle(first)
                .equals(
                        normalizeTitle(second)
                );
    }

    private String normalizeTitle(
            String value
    ) {
        String normalized =
                normalize(value);

        if (
                normalized.startsWith(
                        "campeonato "
                )
        ) {
            normalized =
                    normalized.substring(
                            "campeonato ".length()
                    );
        }

        return normalized;
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

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}