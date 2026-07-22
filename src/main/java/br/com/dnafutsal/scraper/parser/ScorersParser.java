package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.Scorer;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ScorersParser {

    public List<Scorer> parse(Document document) {
        Map<String, Scorer> unique = new LinkedHashMap<>();

        for (Element table : document.select("table")) {
            String normalized = HtmlSupport.normalized(table.text());
            if (!normalized.contains("jogador") || !normalized.contains("clube") || !normalized.contains("total de gols")) {
                continue;
            }

            Map<String, Integer> headers = HtmlSupport.headerIndex(table);
            int playerIndex = HtmlSupport.indexOf(headers, "nome");
            int teamIndex = HtmlSupport.indexOf(headers, "clube", "equipe");
            int goalsIndex = HtmlSupport.indexOf(headers, "total de gols", "gols");
            String phase = HtmlSupport.phaseFor(table);

            for (Element row : HtmlSupport.dataRows(table)) {
                Elements cells = row.select("td");
                if (cells.size() < 3) {
                    continue;
                }

                Element playerCell = HtmlSupport.cellElement(cells, playerIndex);
                Element teamCell = HtmlSupport.cellElement(cells, teamIndex);
                String player = playerCell == null ? null : HtmlSupport.clean(playerCell.text());
                String team = teamCell == null ? null : HtmlSupport.clean(teamCell.text());

                List<Element> images = row.select("img[alt]").stream()
                        .filter(image -> !HtmlSupport.normalized(image.attr("alt")).contains("patrocinador"))
                        .toList();
                if ((player == null || player.isBlank()) && !images.isEmpty()) {
                    player = HtmlSupport.clean(images.get(0).attr("alt"));
                }
                if ((team == null || team.isBlank()) && images.size() > 1) {
                    team = HtmlSupport.clean(images.get(1).attr("alt"));
                }
                Integer goals = HtmlSupport.integer(HtmlSupport.cell(cells, goalsIndex));
                if (player == null || player.isBlank() || team == null || team.isBlank() || goals == null) {
                    continue;
                }

                Scorer scorer = new Scorer(
                        phase,
                        player,
                        images.isEmpty() ? null : HtmlSupport.absoluteUrl(images.get(0), "src"),
                        team,
                        images.size() < 2 ? null : HtmlSupport.absoluteUrl(images.get(1), "src"),
                        goals,
                        false
                );
                unique.putIfAbsent(phase + "|" + player + "|" + team, scorer);
            }
        }
        return new ArrayList<>(unique.values());
    }
}
