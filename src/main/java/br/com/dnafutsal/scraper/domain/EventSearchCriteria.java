package br.com.dnafutsal.scraper.domain;

public record EventSearchCriteria(
        Integer season,
        String title,
        String division,
        String category
) {

    public EventSearchCriteria {
        title = trimToNull(title);
        division = trimToNull(division);
        category = trimToNull(category);

        if (season == null) {
            throw new IllegalArgumentException(
                    "A temporada é obrigatória"
            );
        }
    }

    private static String trimToNull(
            String value
    ) {
        return value == null
                || value.isBlank()
                ? null
                : value.trim();
    }
}