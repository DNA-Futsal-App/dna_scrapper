package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.StandingRow;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StandingsParser {

    public List<StandingRow> parse(Document document) {
        Map<String, StandingRow> unique = new LinkedHashMap<>();

        for (Element table : document.select("table")) {
            String tableText = HtmlSupport.normalized(table.text());
            if (!tableText.contains("pontos") || !tableText.contains("vitorias") || !tableText.contains("gols")) {
                continue;
            }

            Map<String, Integer> headers = HtmlSupport.headerIndex(table);
            int groupIndex = HtmlSupport.indexOf(headers, "chave", "grupo");
            int positionIndex = HtmlSupport.indexOf(headers, "posição", "posicao");
            int teamIndex = HtmlSupport.indexOf(headers, "clube", "equipe");
            int pointsIndex = HtmlSupport.indexOf(headers, "pontos");
            int gamesIndex = HtmlSupport.indexOf(headers, "qtde. jogos", "jogos");
            int winsIndex = HtmlSupport.indexOf(headers, "vitórias", "vitorias");
            int drawsIndex = HtmlSupport.indexOf(headers, "empates");
            int lossesIndex = HtmlSupport.indexOf(headers, "derrotas");
            int goalsForIndex = HtmlSupport.indexOf(headers, "gols pro", "gols marcados");
            int goalsAgainstIndex = HtmlSupport.indexOf(headers, "gols contra", "gols sofridos");
            int goalDifferenceIndex = HtmlSupport.indexOf(headers, "gols saldo", "saldo");
            int averageIndex = HtmlSupport.indexOf(headers, "average");
            int goalsForAverageIndex = HtmlSupport.indexOf(headers, "média gols marcados", "media gols marcados");
            int goalsAgainstAverageIndex = HtmlSupport.indexOf(headers, "média gols sofridos", "media gols sofridos");
            int technicalIndex = HtmlSupport.indexOf(headers, "índice técnico", "indice tecnico");

            if (teamIndex < 0) {
                teamIndex = 2;
            }
            String phase = HtmlSupport.phaseFor(table);

            for (Element row : HtmlSupport.dataRows(table)) {
                Elements cells = row.select("td");
                if (cells.size() < 5) {
                    continue;
                }
                Element teamCell = HtmlSupport.cellElement(cells, teamIndex);
                String team = teamCell == null ? null : HtmlSupport.clean(teamCell.text());
                if (team == null || team.isBlank()) {
                    team = HtmlSupport.bestImageAlt(row);
                }
                if (team == null || team.isBlank()) {
                    continue;
                }

                StandingRow standing = new StandingRow(
                        phase,
                        HtmlSupport.cell(cells, groupIndex),
                        HtmlSupport.integer(HtmlSupport.cell(cells, positionIndex)),
                        team,
                        HtmlSupport.imageUrl(teamCell),
                        HtmlSupport.integer(HtmlSupport.cell(cells, pointsIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, gamesIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, winsIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, drawsIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, lossesIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, goalsForIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, goalsAgainstIndex)),
                        HtmlSupport.integer(HtmlSupport.cell(cells, goalDifferenceIndex)),
                        HtmlSupport.decimal(HtmlSupport.cell(cells, averageIndex)),
                        HtmlSupport.decimal(HtmlSupport.cell(cells, goalsForAverageIndex)),
                        HtmlSupport.decimal(HtmlSupport.cell(cells, goalsAgainstAverageIndex)),
                        HtmlSupport.decimal(HtmlSupport.cell(cells, technicalIndex))
                );
                unique.putIfAbsent(phase + "|" + standing.group() + "|" + standing.position() + "|" + team, standing);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
