package br.com.dnafutsal.scraper.fpfs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FpfsTitlesResponse(
        @JsonProperty("titulos")
        List<FpfsTitleResponse> titles
) {
}