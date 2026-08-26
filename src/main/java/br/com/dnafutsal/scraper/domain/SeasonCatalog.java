package br.com.dnafutsal.scraper.domain;

import java.util.List;

public record SeasonCatalog(
        int season,
        long titleId,
        String titleName,
        List<Division> divisions
) {

    public record Division(
            long id,
            String name,
            List<Category> categories
    ) {
    }

    public record Category(
            long id,
            String name,
            int order,
            long eventId
    ) {
    }
}