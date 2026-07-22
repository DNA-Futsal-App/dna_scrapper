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
            throw new IllegalArgumentException("A temporada é obrigatória");
        }
        if (division != null && title == null) {
            throw new IllegalArgumentException("Informe o título antes da divisão");
        }
        if (category != null && (title == null || division == null)) {
            throw new IllegalArgumentException("Informe título e divisão antes da categoria");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
