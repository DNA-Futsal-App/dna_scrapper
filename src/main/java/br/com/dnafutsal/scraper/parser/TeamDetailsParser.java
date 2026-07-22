package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.PersonEntry;
import br.com.dnafutsal.scraper.domain.TeamDetails;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TeamDetailsParser {

    public TeamDetails parse(
            long eventId,
            long teamId,
            Document document,
            String sourceUrl,
            boolean exposePersonalData,
            String fallbackName,
            String fallbackLogoUrl
    ) {
        String name = findTeamName(document, fallbackName);
        // A listagem de equipes é uma fonte mais precisa para o escudo; a página de detalhes
        // também contém imagens institucionais que não devem ser confundidas com o logo do clube.
        String logo = fallbackLogoUrl;
        if (logo == null) {
            logo = HtmlSupport.imageUrl(document.selectFirst("main,body"));
        }
        String address = null;
        String phone = null;

        for (Element heading : document.select("h1,h2,h3,h4,h5,h6")) {
            String text = HtmlSupport.clean(heading.text());
            String normalized = HtmlSupport.normalized(text);
            if (normalized.startsWith("endereco:")) {
                address = HtmlSupport.valueAfterLabel(text, "Endereço");
            } else if (normalized.startsWith("telefone:")) {
                phone = HtmlSupport.valueAfterLabel(text, "Telefone");
            }
        }

        if (!exposePersonalData) {
            return new TeamDetails(
                    eventId, teamId, name, logo, null, null, List.of(), List.of(), true, sourceUrl
            );
        }

        List<PersonEntry> athletes = new ArrayList<>();
        List<PersonEntry> staff = new ArrayList<>();
        for (Element table : document.select("table")) {
            Map<String, Integer> headers = HtmlSupport.headerIndex(table);
            int nameIndex = HtmlSupport.indexOf(headers, "nome");
            int nicknameIndex = HtmlSupport.indexOf(headers, "apelido");
            int roleIndex = HtmlSupport.indexOf(headers, "função", "funcao");
            if (nameIndex < 0) {
                continue;
            }

            boolean staffTable = roleIndex >= 0 || HtmlSupport.normalized(table.text()).contains("dirigentes");
            for (Element row : HtmlSupport.dataRows(table)) {
                Elements cells = row.select("td");
                String personName = HtmlSupport.cell(cells, nameIndex);
                if (personName == null || personName.isBlank()) {
                    personName = HtmlSupport.bestImageAlt(row);
                }
                if (personName == null || personName.isBlank()) {
                    continue;
                }

                PersonEntry person = new PersonEntry(
                        personName,
                        HtmlSupport.cell(cells, nicknameIndex),
                        HtmlSupport.cell(cells, roleIndex),
                        HtmlSupport.imageUrl(row)
                );
                if (staffTable) {
                    staff.add(person);
                } else {
                    athletes.add(person);
                }
            }
        }

        return new TeamDetails(
                eventId, teamId, name, logo, address, phone,
                List.copyOf(athletes), List.copyOf(staff), false, sourceUrl
        );
    }

    private String findTeamName(Document document, String fallbackName) {
        if (fallbackName != null && !fallbackName.isBlank()) {
            return fallbackName;
        }
        Element title = document.selectFirst("title");
        if (title != null) {
            String text = HtmlSupport.clean(title.text());
            if (text != null && !HtmlSupport.normalized(text).contains("federacao paulista")) {
                return text;
            }
        }

        Element firstImage = document.selectFirst("main img[alt],body img[alt]");
        String alt = firstImage == null ? null : HtmlSupport.clean(firstImage.attr("alt"));
        return alt == null || alt.isBlank() ? "Equipe " + document.location() : alt;
    }
}
