package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.domain.Game;
import br.com.dnafutsal.scraper.domain.Scorer;
import br.com.dnafutsal.scraper.domain.StandingRow;
import br.com.dnafutsal.scraper.domain.TeamDetails;
import br.com.dnafutsal.scraper.domain.TeamSummary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParserContractTest {

    private static final String BASE = "https://eventos.admfutsal.com.br";

    @Test
    void parsesEventMetadata() {
        Document document = Jsoup.parse("""
                <html><body>
                  <h5>Campeonato Paulista, Temporada 2026</h5>
                  <h6>Categoria Principal, Divisao A1</h6>
                  <p>Meus Favoritos Adicionar aos Favoritos</p>
                </body></html>
                """, BASE + "/evento/917");

        EventMetadata event = new EventMetadataParser().parse(917, document, document.location());

        assertThat(event.title()).isEqualTo("Campeonato Paulista");
        assertThat(event.season()).isEqualTo(2026);
        assertThat(event.category()).isEqualTo("Principal");
        assertThat(event.division()).isEqualTo("A1");
    }

    @Test
    void parsesStandingsTable() {
        Document document = Jsoup.parse("""
                <html><body>
                  <a href="#fase-1">1ª Fase de Classificação</a>
                  <div id="fase-1"><table>
                    <thead><tr>
                      <th>Chave</th><th>Posição</th><th>Clube</th><th>Pontos</th>
                      <th>Qtde. Jogos</th><th>Vitórias</th><th>Empates</th><th>Derrotas</th>
                      <th>Gols Pro</th><th>Gols Contra</th><th>Gols Saldo</th><th>Average</th>
                      <th>Média Gols Marcados</th><th>Média Gols Sofridos</th><th>Índice Técnico</th>
                    </tr></thead>
                    <tbody><tr>
                      <td>GRUPO A</td><td>1º</td>
                      <td><img src="/logos/time-a.png" alt="TIME A">TIME A</td>
                      <td>24</td><td>8</td><td>8</td><td>0</td><td>0</td>
                      <td>42</td><td>9</td><td>33</td><td>4.67</td><td>5.25</td><td>1.13</td><td>3</td>
                    </tr></tbody>
                  </table></div>
                </body></html>
                """, BASE + "/evento/917");

        List<StandingRow> rows = new StandingsParser().parse(document);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).phase()).isEqualTo("1ª Fase de Classificação");
        assertThat(rows.get(0).team()).isEqualTo("TIME A");
        assertThat(rows.get(0).points()).isEqualTo(24);
        assertThat(rows.get(0).goalDifference()).isEqualTo(33);
    }

    @Test
    void parsesPlayedAndScheduledGames() {
        Document document = Jsoup.parse("""
                <html><body>
                  <a href="#fase-1">FASE DE CLASSIFICAÇÃO</a>
                  <div id="fase-1"><table>
                    <thead><tr><th>Data</th><th>Horário</th><th>Ginásio</th><th>Resultado</th></tr></thead>
                    <tbody>
                      <tr><td>07/04</td><td>19:30h</td><td>GINÁSIO A</td><td>
                        <img src="/logos/a.png" alt="TIME A">TIME A 2 x 2
                        <img src="/logos/b.png" alt="TIME B">TIME B
                        <a href="https://admfutsal.com.br/sumula_online/sumula_imprimir.php?id_jogo=123">Ver Súmula</a>
                      </td></tr>
                      <tr><td>08/04</td><td>20:00h</td><td>GINÁSIO B</td><td>
                        <img src="/logos/c.png" alt="TIME C">TIME C x
                        <img src="/logos/d.png" alt="TIME D">TIME D
                      </td></tr>
                    </tbody>
                  </table></div>
                </body></html>
                """, BASE + "/evento/917/jogos");
        EventMetadata event = new EventMetadata(917, "Campeonato Paulista", 2026, "Principal", "A1", BASE);

        List<Game> games = new GamesParser().parse(document, event);

        assertThat(games).hasSize(2);
        assertThat(games.get(0).date()).isEqualTo(LocalDate.of(2026, 4, 7));
        assertThat(games.get(0).time()).isEqualTo(LocalTime.of(19, 30));
        assertThat(games.get(0).homeScore()).isEqualTo(2);
        assertThat(games.get(0).awayScore()).isEqualTo(2);
        assertThat(games.get(0).gameId()).isEqualTo(123L);
        assertThat(games.get(1).homeScore()).isNull();
        assertThat(games.get(1).awayScore()).isNull();
    }

    @Test
    void parsesTeamsAndScorers() {
        Document teamsDocument = Jsoup.parse("""
                <html><body>
                  <a href="/evento/917/equipe/55"><img src="/logos/a.png" alt="TIME A">TIME A</a>
                  <a href="/evento/917/equipe/56"><img src="/logos/b.png" alt="TIME B">TIME B</a>
                </body></html>
                """, BASE + "/evento/917/equipes");

        List<TeamSummary> teams = new TeamsParser().parse(917, teamsDocument);
        assertThat(teams).extracting(TeamSummary::teamId).containsExactly(55L, 56L);

        Document scorersDocument = Jsoup.parse("""
                <html><body>
                  <a href="#fase">FASE DE CLASSIFICAÇÃO</a>
                  <div id="fase"><table>
                    <thead><tr><th>Jogador</th><th>Nome</th><th>Clube</th><th>Total de Gols</th></tr></thead>
                    <tbody><tr>
                      <td><img src="/players/1.png" alt="JOGADOR UM"></td><td>JOGADOR UM</td>
                      <td><img src="/logos/a.png" alt="TIME A">TIME A</td><td>9</td>
                    </tr></tbody>
                  </table></div>
                </body></html>
                """, BASE + "/evento/917/artilharia");

        List<Scorer> scorers = new ScorersParser().parse(scorersDocument);
        assertThat(scorers).hasSize(1);
        assertThat(scorers.get(0).player()).isEqualTo("JOGADOR UM");
        assertThat(scorers.get(0).goals()).isEqualTo(9);
    }

    @Test
    void suppressesOrExposesTeamPersonalData() {
        Document document = Jsoup.parse("""
                <html><body><main>
                  <img src="/logos/a.png" alt="TIME A">
                  <h4>Endereço: Rua Exemplo, 10</h4><h4>Telefone: (11) 99999-9999</h4>
                  <h3>Atletas</h3>
                  <table><thead><tr><th>#</th><th>Nome</th><th>Apelido</th></tr></thead>
                    <tbody><tr><td><img src="/players/1.png" alt="JOGADOR UM"></td><td>JOGADOR UM</td><td>UM</td></tr></tbody>
                  </table>
                  <h3>Comissão Técnica</h3>
                  <table><thead><tr><th>Nome</th><th>Função</th></tr></thead>
                    <tbody><tr><td>TÉCNICO UM</td><td>Técnico</td></tr></tbody>
                  </table>
                </main></body></html>
                """, BASE + "/evento/917/equipe/55");
        TeamDetailsParser parser = new TeamDetailsParser();

        TeamDetails hidden = parser.parse(917, 55, document, document.location(), false, "TIME A", null);
        TeamDetails visible = parser.parse(917, 55, document, document.location(), true, "TIME A", null);

        assertThat(hidden.personalDataSuppressed()).isTrue();
        assertThat(hidden.address()).isNull();
        assertThat(hidden.athletes()).isEmpty();
        assertThat(visible.address()).isEqualTo("Rua Exemplo, 10");
        assertThat(visible.athletes()).hasSize(1);
        assertThat(visible.staff()).hasSize(1);
    }
}
