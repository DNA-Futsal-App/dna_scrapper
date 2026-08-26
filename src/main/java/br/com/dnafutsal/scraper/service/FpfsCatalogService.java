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
}