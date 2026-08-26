package br.com.dnafutsal.scraper.fpfs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FpfsCategoryEventResponse(

        @JsonProperty("id_evento")
        long eventId,

        @JsonProperty("id_titulo")
        long titleId,

        @JsonProperty("id_divisao")
        long divisionId,

        @JsonProperty("id_categoria")
        long categoryId,

        @JsonProperty("temporada")
        int season,

        @JsonProperty("status")
        String status,

        @JsonProperty("categoria")
        FpfsCategoryResponse category

) {
}