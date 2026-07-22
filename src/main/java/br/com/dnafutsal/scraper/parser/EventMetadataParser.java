package br.com.dnafutsal.scraper.parser;

import br.com.dnafutsal.scraper.domain.EventMetadata;
import br.com.dnafutsal.scraper.exception.ScrapingParseException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EventMetadataParser {

    private static final Pattern TITLE_AND_SEASON = Pattern.compile("(?i)(.+?)\\s*,\\s*Temporada\\s*(\\d{4})");
    private static final Pattern CATEGORY_AND_DIVISION = Pattern.compile(
            "(?i)^Categoria\\s+(.+?)\\s*,\\s*Divis(?:a|ã)o\\s+(.+?)$"
    );
    private static final Pattern CATEGORY_FALLBACK = Pattern.compile(
            "(?i)Categoria\\s+(.+?)\\s*,\\s*Divis(?:a|ã)o\\s+(A\\d+|Única|Unica|[^\\s,;|]{1,20})"
    );

    public EventMetadata parse(long eventId, Document document, String sourceUrl) {
        String heading = findEventHeading(document);
        Matcher titleMatcher = TITLE_AND_SEASON.matcher(heading);
        if (!titleMatcher.find()) {
            throw new ScrapingParseException("Não foi possível identificar título e temporada do evento " + eventId);
        }

        String[] categoryAndDivision = findCategoryAndDivision(document);
        return new EventMetadata(
                eventId,
                HtmlSupport.clean(titleMatcher.group(1)),
                Integer.parseInt(titleMatcher.group(2)),
                categoryAndDivision[0],
                categoryAndDivision[1],
                sourceUrl
        );
    }

    private String findEventHeading(Document document) {
        for (Element element : document.select("h1,h2,h3,h4,h5,h6")) {
            String text = HtmlSupport.clean(element.text());
            if (text != null && HtmlSupport.normalized(text).contains("temporada")) {
                return text;
            }
        }
        return document.body().text();
    }

    private String[] findCategoryAndDivision(Document document) {
        for (Element element : document.select("h1,h2,h3,h4,h5,h6,p,strong")) {
            Matcher matcher = CATEGORY_AND_DIVISION.matcher(HtmlSupport.clean(element.text()));
            if (matcher.find()) {
                return new String[]{HtmlSupport.clean(matcher.group(1)), HtmlSupport.clean(matcher.group(2))};
            }
        }

        Matcher fallback = CATEGORY_FALLBACK.matcher(document.body().text());
        if (fallback.find()) {
            return new String[]{HtmlSupport.clean(fallback.group(1)), HtmlSupport.clean(fallback.group(2))};
        }
        throw new ScrapingParseException("Não foi possível identificar categoria e divisão do evento");
    }
}
