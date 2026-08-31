package br.com.dnafutsal.scraper.fpfs;

import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FpfsCatalogClientTest {

    private static final String BASE_URL =
            "https://fpfs.example";

    private MockRestServiceServer server;
    private FpfsCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(
                                BASE_URL
                        );

        server =
                MockRestServiceServer
                        .bindTo(builder)
                        .build();

        client =
                new FpfsCatalogClient(
                        builder.build()
                );
    }

    @Test
    void mapsTitlesFromFpfsContract() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_titulos/2026"
                        )
                )
                .andExpect(
                        method(
                                HttpMethod.GET
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "titulos": [
                                    {
                                      "id_titulo": 16,
                                      "nome": "Paulista",
                                      "status": "A"
                                    },
                                    {
                                      "id_titulo": 25,
                                      "nome": "COPA UNIÃO",
                                      "status": "A"
                                    }
                                  ],
                                  "campo_desconhecido": true
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        var result =
                client.titles(2026);

        assertThat(result)
                .hasSize(2);

        assertThat(
                result.get(0).id()
        ).isEqualTo(16);

        assertThat(
                result.get(0).name()
        ).isEqualTo(
                "Paulista"
        );

        server.verify();
    }

    @Test
    void parsesNestedWrappedDivisionsAndRemovesDuplicates() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_divisoes/2026/16"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "divisoes": [
                                    {
                                      "id_divisao": 3,
                                      "divisao": {
                                        "id_divisao": 3,
                                        "nome": "A1",
                                        "status": "A"
                                      }
                                    },
                                    {
                                      "id_divisao": 3,
                                      "divisao": {
                                        "id_divisao": 3,
                                        "nome": "A1",
                                        "status": "A"
                                      }
                                    },
                                    {
                                      "id_divisao": 4,
                                      "nome": "A2",
                                      "status": "A"
                                    }
                                  ]
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        var result =
                client.divisions(
                        2026,
                        16
                );

        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .extracting(
                        division ->
                                division.id()
                )
                .containsExactly(
                        3L,
                        4L
                );

        assertThat(result)
                .extracting(
                        division ->
                                division.name()
                )
                .containsExactly(
                        "A1",
                        "A2"
                );

        server.verify();
    }

    @Test
    void parsesFlatDivisionArray() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_divisoes/2026/16"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                [
                                  {
                                    "id_divisao": 3,
                                    "nome": "A1",
                                    "status": "A"
                                  }
                                ]
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        var result =
                client.divisions(
                        2026,
                        16
                );

        assertThat(result)
                .singleElement()
                .satisfies(division -> {
                    assertThat(
                            division.id()
                    ).isEqualTo(3);

                    assertThat(
                            division.name()
                    ).isEqualTo("A1");
                });

        server.verify();
    }

    @Test
    void mapsCategoryEvents() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_categorias/2026/16/3"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                [
                                  {
                                    "id_evento": 917,
                                    "id_titulo": 16,
                                    "id_divisao": 3,
                                    "id_categoria": 7,
                                    "temporada": 2026,
                                    "status": "A",
                                    "categoria": {
                                      "id_categoria": 7,
                                      "nome": "Principal",
                                      "ordem_execucao": 10,
                                      "status": "A"
                                    }
                                  }
                                ]
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        var result =
                client.categoryEvents(
                        2026,
                        16,
                        3
                );

        assertThat(result)
                .singleElement()
                .satisfies(event -> {
                    assertThat(
                            event.eventId()
                    ).isEqualTo(917);

                    assertThat(
                            event.category().id()
                    ).isEqualTo(7);

                    assertThat(
                            event.category().name()
                    ).isEqualTo(
                            "Principal"
                    );
                });

        server.verify();
    }

    @Test
    void rejectsInvalidDivisionPayload() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_divisoes/2026/16"
                        )
                )
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "unexpected": []
                                }
                                """,
                                MediaType.APPLICATION_JSON
                        )
                );

        assertThatThrownBy(
                () -> client.divisions(
                        2026,
                        16
                )
        )
                .isInstanceOf(
                        UpstreamAccessException.class
                )
                .hasMessageContaining(
                        "formato inválido"
                );

        server.verify();
    }

    @Test
    void wrapsFpfsHttpFailure() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_titulos/2026"
                        )
                )
                .andRespond(
                        withStatus(
                                HttpStatus.SERVICE_UNAVAILABLE
                        )
                );

        assertThatThrownBy(
                () -> client.titles(
                        2026
                )
        )
                .isInstanceOf(
                        UpstreamAccessException.class
                )
                .hasMessageContaining(
                        "HTTP 503"
                );

        server.verify();
    }

    @Test
    void wrapsMalformedJson() {
        server.expect(
                        requestTo(
                                BASE_URL
                                        + "/api/get_titulos/2026"
                        )
                )
                .andRespond(
                        withSuccess(
                                "{broken-json",
                                MediaType.APPLICATION_JSON
                        )
                );

        assertThatThrownBy(
                () -> client.titles(
                        2026
                )
        )
                .isInstanceOf(
                        UpstreamAccessException.class
                );

        server.verify();
    }
}