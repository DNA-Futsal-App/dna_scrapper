package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.domain.Game;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GamesParser {

    private static final Pattern DATE = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)");
    private static final Pattern TIME = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})h?(?!\\d)");
    private static final Pattern SCORE = Pattern.compile(
            "(?<![\\p{L}\\d])(-|\\d+)?\\s+x\\s+(-|\\d+)?(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE
    );

    public List<Game> parse(Document document, EventMetadata event) {
        Map<String, Game> unique = new LinkedHashMap<>();

        for (Element table : document.select("table")) {
            String normalized = HtmlSupport.normalized(table.text());
            if (!normalized.contains("data") || !normalized.contains("horario")
                    || !normalized.contains("ginasio") || !normalized.contains("resultado")) {
                continue;
            }

            Map<String, Integer> headers = HtmlSupport.headerIndex(table);
            int dateIndex = HtmlSupport.indexOf(headers, "data");
            int timeIndex = HtmlSupport.indexOf(headers, "horário", "horario");
            int venueIndex = HtmlSupport.indexOf(headers, "ginásio", "ginasio");
            int resultIndex = HtmlSupport.indexOf(headers, "resultado");
            String phase = HtmlSupport.phaseFor(table);

            for (Element row : HtmlSupport.dataRows(table)) {
                String rowText = HtmlSupport.clean(row.text());
                Matcher dateMatcher = DATE.matcher(rowText);
                Matcher timeMatcher = TIME.matcher(rowText);
                Matcher scoreMatcher = SCORE.matcher(rowText);
                if (!dateMatcher.find()) {
                    continue;
                }
                boolean hasScoreExpression = scoreMatcher.find();

                Elements cells = row.select("td");
                LocalDate date = toDate(event.season(), dateMatcher);
                LocalTime time = timeMatcher.find() ? toTime(timeMatcher) : null;
                String venue = HtmlSupport.cell(cells, venueIndex);
                if (venue == null || venue.isBlank()) {
                    venue = deriveVenue(rowText, dateMatcher.group(), time == null ? null : time.toString());
                }

                Element sumulaLink = row.selectFirst("a[href*='sumula_imprimir.php'],a:matchesOwn((?i)Ver\\s+Súmula)");
                String sheetUrl = HtmlSupport.absoluteUrl(sumulaLink, "href");
                Long gameId = HtmlSupport.queryLong(sheetUrl, "id_jogo");

                List<Element> usefulImages = row.select("img[alt]").stream()
                        .filter(image -> {
                            String alt = HtmlSupport.normalized(image.attr("alt"));
                            return !alt.isBlank() && !alt.contains("patrocinador") && !alt.contains("carregando");
                        })
                        .toList();

                String homeTeam = usefulImages.size() > 0 ? HtmlSupport.clean(usefulImages.get(0).attr("alt")) : null;
                String awayTeam = usefulImages.size() > 1 ? HtmlSupport.clean(usefulImages.get(1).attr("alt")) : null;
                String homeLogo = usefulImages.size() > 0 ? HtmlSupport.absoluteUrl(usefulImages.get(0), "src") : null;
                String awayLogo = usefulImages.size() > 1 ? HtmlSupport.absoluteUrl(usefulImages.get(1), "src") : null;

                if ((homeTeam == null || awayTeam == null) && hasScoreExpression) {
                    String resultText = HtmlSupport.cell(cells, resultIndex);
                    String[] names = namesAroundScore(resultText == null ? rowText : resultText);
                    homeTeam = homeTeam == null ? names[0] : homeTeam;
                    awayTeam = awayTeam == null ? names[1] : awayTeam;
                }
                if (homeTeam == null && awayTeam == null) {
                    continue;
                }

                Integer homeScore = hasScoreExpression ? scoreValue(scoreMatcher.group(1)) : null;
                Integer awayScore = hasScoreExpression ? scoreValue(scoreMatcher.group(2)) : null;
                boolean walkover = HtmlSupport.normalized(rowText).contains("w.o");
                Game game = new Game(
                        gameId,
                        phase,
                        date,
                        time,
                        venue,
                        homeTeam,
                        homeLogo,
                        homeScore,
                        awayTeam,
                        awayLogo,
                        awayScore,
                        walkover,
                        sheetUrl
                );
                String key = gameId != null ? "id:" + gameId : phase + "|" + date + "|" + time + "|" + homeTeam + "|" + awayTeam;
                unique.putIfAbsent(key, game);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private LocalDate toDate(int season, Matcher matcher) {
        try {
            return LocalDate.of(season, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(1)));
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private LocalTime toTime(Matcher matcher) {
        try {
            return LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private String deriveVenue(String rowText, String dateText, String timeText) {
        String value = rowText.replaceFirst(Pattern.quote(dateText), "");
        if (timeText != null) {
            value = value.replaceFirst(Pattern.quote(timeText), "");
        }
        Matcher score = SCORE.matcher(value);
        if (score.find()) {
            value = value.substring(0, score.start());
        }
        return HtmlSupport.clean(value);
    }

    private Integer scoreValue(String value) {
        return value == null || value.equals("-") ? null : Integer.valueOf(value);
    }

    private String[] namesAroundScore(String text) {
        if (text == null) {
            return new String[]{null, null};
        }
        Matcher matcher = SCORE.matcher(text);
        if (!matcher.find()) {
            return new String[]{null, null};
        }
        String before = HtmlSupport.clean(text.substring(0, matcher.start()));
        String after = HtmlSupport.clean(text.substring(matcher.end()));
        return new String[]{before, after};
    }
}
