package br.com.dnafutsal.scraper.domain;

import java.util.List;

public record TeamDetails(
        long eventId,
        long teamId,
        String name,
        String logoUrl,
        String address,
        String phone,
        List<PersonEntry> athletes,
        List<PersonEntry> staff,
        boolean personalDataSuppressed,
        String sourceUrl
) {
}
