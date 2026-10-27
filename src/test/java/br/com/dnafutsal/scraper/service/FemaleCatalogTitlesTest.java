package br.com.dnafutsal.scraper.service;

import br.com.dnafutsal.scraper.fpfs.FpfsCatalogClient;
import br.com.dnafutsal.scraper.fpfs.dto.FpfsTitleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FemaleCatalogTitlesTest {

    @Test
    void listsOnlyActiveOfficialTitlesWithoutDuplicateIds() {
        FpfsCatalogClient fpfs = mock(FpfsCatalogClient.class);
        when(fpfs.titles(2026)).thenReturn(List.of(
                new FpfsTitleResponse(16, "Paulista", "A"),
                new FpfsTitleResponse(24, "Campeonato Paulista Feminino", "A"),
                new FpfsTitleResponse(24, "Campeonato Paulista Feminino", "A"),
                new FpfsTitleResponse(25, "Copa União", "I"),
                new FpfsTitleResponse(30, "", "A")
        ));

        FpfsCatalogLoader loader = new FpfsCatalogLoader(fpfs);
        FpfsCatalogService service = new FpfsCatalogService(loader);

        var availableTitles = service.titles(2026);
        assertThat(availableTitles)
                .extracting(option -> option.id())
                .containsExactly(16L, 24L);
        assertThat(availableTitles)
                .extracting(option -> option.name())
                .containsExactly("Paulista", "Campeonato Paulista Feminino");
        verify(fpfs).titles(2026);
    }

    @Test
    void resolvesOfficialPrefixForBothGendersWithoutConfusingCopaPaulista() {
        FpfsCatalogClient fpfs = mock(FpfsCatalogClient.class);
        when(fpfs.titles(2026)).thenReturn(List.of(
                new FpfsTitleResponse(19, "Copa Paulista", "A"),
                new FpfsTitleResponse(16, "Campeonato Paulista", "A"),
                new FpfsTitleResponse(24, "Campeonato Paulista Feminino", "A")
        ));
        when(fpfs.divisions(2026, 16)).thenReturn(List.of());
        when(fpfs.divisions(2026, 24)).thenReturn(List.of());

        FpfsCatalogLoader loader = new FpfsCatalogLoader(fpfs);

        assertThat(loader.loadPaulista(2026, "Paulista").titleId()).isEqualTo(16);
        assertThat(loader.loadPaulista(2026, "Paulista Feminino").titleId()).isEqualTo(24);
        assertThatThrownBy(() -> loader.loadPaulista(2026, "Paulista Masculino"))
                .hasMessageContaining("não foi encontrado");
    }
}
