package br.com.dnafutsal.scraper.fpfs;

import br.com.dnafutsal.scraper.config.ScraperProperties;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsCategoryEventResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsDivisionResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsTitleResponse;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsTitlesResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FpfsCatalogClient {

    private final RestClient client;

    @Autowired
    public FpfsCatalogClient(
            RestClient.Builder builder,
            ScraperProperties properties
    ) {
        this(
                createClient(
                        builder,
                        properties
                )
        );
    }

    FpfsCatalogClient(
            RestClient client
    ) {
        this.client = client;
    }

    private static RestClient createClient(
            RestClient.Builder builder,
            ScraperProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                properties.requestTimeout()
        );

        requestFactory.setReadTimeout(
                properties.requestTimeout()
        );

        return builder.clone()
                .requestFactory(
                        requestFactory
                )
                .baseUrl(
                        properties.baseUrl()
                )
                .build();
    }

    public List<FpfsTitleResponse> titles(int season) {

        FpfsTitlesResponse response =
                get(
                        "/api/get_titulos/{season}",
                        FpfsTitlesResponse.class,
                        season
                );

        if (response == null || response.titles() == null) {
            return List.of();
        }

        return response.titles();
    }

    public List<FpfsDivisionResponse> divisions(
            int season,
            long titleId
    ) {
        JsonNode response =
                get(
                        "/api/get_divisoes/{season}/{titleId}",
                        JsonNode.class,
                        season,
                        titleId
                );

        return parseDivisions(response);
    }

    public List<FpfsCategoryEventResponse> categoryEvents(
            int season,
            long titleId,
            long divisionId
    ) {
        try {
            List<FpfsCategoryEventResponse> response =
                    client.get()
                            .uri(
                                    "/api/get_categorias/{season}/{titleId}/{divisionId}",
                                    season,
                                    titleId,
                                    divisionId
                            )
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<>() {
                                    }
                            );

            return response == null
                    ? List.of()
                    : response;

        } catch (RestClientResponseException exception) {

            throw new UpstreamAccessException(
                    "A FPFS recusou a consulta de categorias. HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );

        } catch (ResourceAccessException exception) {

            throw new UpstreamAccessException(
                    "Não foi possível conectar à API de categorias da FPFS",
                    exception
            );

        } catch (RestClientException exception) {

            throw new UpstreamAccessException(
                    "Falha ao interpretar a resposta de categorias da FPFS",
                    exception
            );
        }
    }

    private <T> T get(
            String path,
            Class<T> type,
            Object... variables
    ) {
        try {
            return client.get()
                    .uri(path, variables)
                    .retrieve()
                    .body(type);

        } catch (RestClientResponseException exception) {

            throw new UpstreamAccessException(
                    "A FPFS recusou a requisição. HTTP "
                            + exception.getStatusCode().value(),
                    exception
            );

        } catch (ResourceAccessException exception) {

            throw new UpstreamAccessException(
                    "Não foi possível conectar à API da FPFS",
                    exception
            );

        } catch (RestClientException exception) {

            throw new UpstreamAccessException(
                    "Falha ao interpretar resposta da API da FPFS",
                    exception
            );
        }
    }

    private List<FpfsDivisionResponse> parseDivisions(
            JsonNode root
    ) {
        if (root == null || root.isNull()) {
            return List.of();
        }

        JsonNode array = locateArray(
                root,
                "divisoes",
                "data",
                "results"
        );

        if (array == null || !array.isArray()) {
            throw new UpstreamAccessException(
                    "A API da FPFS retornou um formato inválido para divisões"
            );
        }

        Map<Long, FpfsDivisionResponse> divisions =
                new LinkedHashMap<>();

        for (JsonNode item : array) {

            JsonNode source =
                    item.has("divisao")
                            && item.get("divisao").isObject()
                            ? item.get("divisao")
                            : item;

            Long id = firstLong(
                    source,
                    "id_divisao",
                    "id",
                    "value"
            );

            if (id == null) {
                id = firstLong(
                        item,
                        "id_divisao",
                        "id",
                        "value"
                );
            }

            String name = firstText(
                    source,
                    "nome",
                    "descricao",
                    "label"
            );

            if (name == null) {
                name = firstText(
                        item,
                        "nome",
                        "descricao",
                        "label"
                );
            }

            String status = firstText(
                    source,
                    "status"
            );

            if (status == null) {
                status = firstText(
                        item,
                        "status"
                );
            }

            if (id == null || name == null || name.isBlank()) {
                continue;
            }

            divisions.putIfAbsent(
                    id,
                    new FpfsDivisionResponse(
                            id,
                            name.trim(),
                            status
                    )
            );
        }

        if (divisions.isEmpty()) {
            throw new UpstreamAccessException(
                    "A resposta de divisões da FPFS não contém id e nome de divisão"
            );
        }

        return List.copyOf(
                divisions.values()
        );
    }

    private JsonNode locateArray(
            JsonNode root,
            String... names
    ) {
        if (root.isArray()) {
            return root;
        }

        for (String name : names) {
            JsonNode candidate =
                    root.get(name);

            if (candidate != null
                    && candidate.isArray()) {

                return candidate;
            }
        }

        return null;
    }

    private Long firstLong(
            JsonNode node,
            String... names
    ) {
        if (node == null) {
            return null;
        }

        for (String name : names) {

            JsonNode candidate =
                    node.get(name);

            if (candidate != null
                    && candidate.canConvertToLong()) {

                return candidate.longValue();
            }
        }

        return null;
    }

    private String firstText(
            JsonNode node,
            String... names
    ) {
        if (node == null) {
            return null;
        }

        for (String name : names) {

            JsonNode candidate =
                    node.get(name);

            if (candidate != null
                    && candidate.isValueNode()) {

                String value =
                        candidate.asText();

                if (!value.isBlank()) {
                    return value.trim();
                }
            }
        }

        return null;
    }
}