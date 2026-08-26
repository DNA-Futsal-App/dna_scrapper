package br.com.dnafutsal.scraper.fpfs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FpfsCategoryResponse(

        @JsonProperty("id_categoria")
        long id,

        @JsonProperty("nome")
        String name,

        @JsonProperty("ordem_execucao")
        Integer executionOrder,

        @JsonProperty("status")
        String status

) {
}