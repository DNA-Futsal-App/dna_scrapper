package br.com.dnafutsal.scraper.api;

import br.com.dnafutsal.scraper.domain.CategoryTeams;
import br.com.dnafutsal.scraper.service.FpfsCatalogService;
import br.com.dnafutsal.scraper.service.FutsalScraperService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    @Autowired
    private final FutsalScraperService service;
    private final FpfsCatalogService catalogService;

    public CatalogController(FutsalScraperService service, FpfsCatalogService fpfsCatalogService) {
        this.service = service;
        this.catalogService = fpfsCatalogService;
    }

    @GetMapping({"/division", "/divisions"})
    public List<CatalogOption> divisions(
            @RequestParam(required = false) @Min(2016) @Max(2100) Integer season
    ) {
        return catalogService.divisions(resolveSeason(season));
    }

    @GetMapping("/categories")
    public List<CatalogOption> categories(
            @RequestParam(required = false) @Min(2016) @Max(2100) Integer season,
            @RequestParam(required = false) String division
    ) {
        return catalogService.categories(resolveSeason(season), division);
    }

    @GetMapping
    public List<CategoryTeams> teamsByCategory(
            @RequestParam(required = false) @Min(2016) @Max(2100) Integer season,
            @RequestParam String division,
            @RequestParam String category
    ) {
        return service.categoryTeams(resolveSeason(season), division, category);
    }

    private int resolveSeason(Integer season) {
        return season == null ? LocalDate.now().getYear() : season;
    }
}
