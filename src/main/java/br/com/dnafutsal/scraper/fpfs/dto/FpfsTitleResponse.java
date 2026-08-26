package br.com.dnafutsal.scraper.fpfs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FpfsTitleResponse(

        @JsonProperty("id_titulo")
        long id,

        @JsonProperty("nome")
        String name,

        @JsonProperty("status")
        String status

) {
}