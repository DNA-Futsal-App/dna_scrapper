package br.com.dnafutsal.scraper.api;

import br.com.dnafutsal.scraper.service.FpfsCatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final FpfsCatalogService catalogService;

    public CatalogController(
            FpfsCatalogService catalogService
    ) {
        this.catalogService =
                catalogService;
    }

    @GetMapping("/divisions")
    public List<CatalogOption> divisions(
            @RequestParam(required = false)
            @Min(2016)
            @Max(2100)
            Integer season
    ) {
        return catalogService.divisions(
                resolveSeason(season)
        );
    }

    @GetMapping("/categories")
    public List<CatalogCategoryOption> categories(
            @RequestParam(required = false)
            @Min(2016)
            @Max(2100)
            Integer season,

            @RequestParam
            @Positive
            long divisionId
    ) {
        return catalogService.categories(
                resolveSeason(season),
                divisionId
        );
    }

    private int resolveSeason(
            Integer season
    ) {
        return season == null
                ? LocalDate.now().getYear()
                : season;
    }
}