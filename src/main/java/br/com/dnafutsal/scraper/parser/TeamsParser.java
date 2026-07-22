package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.TeamSummary;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TeamsParser {

    private static final Pattern TEAM_ID = Pattern.compile("/equipe/(\\d+)(?:/|$)");

    public List<TeamSummary> parse(long eventId, Document document) {
        Map<Long, TeamSummary> unique = new LinkedHashMap<>();
        String eventPath = "/evento/" + eventId + "/equipe/";

        for (Element link : document.select("a[href*='" + eventPath + "']")) {
            String url = HtmlSupport.absoluteUrl(link, "href");
            Matcher matcher = TEAM_ID.matcher(url == null ? "" : url);
            if (!matcher.find()) {
                continue;
            }
            long teamId = Long.parseLong(matcher.group(1));
            String name = HtmlSupport.clean(link.text());
            if (name == null || name.isBlank()) {
                name = HtmlSupport.bestImageAlt(link);
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            unique.putIfAbsent(teamId, new TeamSummary(teamId, name, HtmlSupport.imageUrl(link), url));
        }
        return new ArrayList<>(unique.values());
    }
}
