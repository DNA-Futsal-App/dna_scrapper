package br.com.dnafutsal.scraper.parser;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HtmlSupport {

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    private HtmlSupport() {
    }

    static String clean(String value) {
        return value == null ? null : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    static String normalized(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    static Integer integer(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = INTEGER.matcher(value.replace("º", "").replace("ª", ""));
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    static Long longValue(String value) {
        Integer number = integer(value);
        return number == null ? null : number.longValue();
    }

    static Double decimal(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = DECIMAL.matcher(value);
        return matcher.find() ? Double.valueOf(matcher.group().replace(',', '.')) : null;
    }

    static String absoluteUrl(Element element, String attribute) {
        if (element == null) {
            return null;
        }
        String absolute = element.absUrl(attribute);
        return absolute.isBlank() ? null : absolute;
    }

    static String imageUrl(Element scope) {
        if (scope == null) {
            return null;
        }
        for (Element image : scope.select("img[src]")) {
            String alt = normalized(image.attr("alt"));
            String src = normalized(image.attr("src"));
            if (!alt.contains("patrocinador") && !alt.contains("carregando")
                    && !src.contains("patrocinador") && !src.contains("loading")) {
                return absoluteUrl(image, "src");
            }
        }
        return null;
    }

    static String bestImageAlt(Element scope) {
        if (scope == null) {
            return null;
        }
        for (Element image : scope.select("img[alt]")) {
            String alt = clean(image.attr("alt"));
            if (alt != null && !alt.isBlank() && !normalized(alt).contains("patrocinador")) {
                return alt;
            }
        }
        return null;
    }

    static Map<String, Integer> headerIndex(Element table) {
        Element headerRow = table.selectFirst("thead tr");
        if (headerRow == null) {
            headerRow = table.selectFirst("tr:has(th)");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        if (headerRow == null) {
            return result;
        }
        Elements cells = headerRow.select("th,td");
        for (int index = 0; index < cells.size(); index++) {
            result.put(normalized(cells.get(index).text()), index);
        }
        return result;
    }

    static List<Element> dataRows(Element table) {
        Elements rows = table.select("tbody > tr");
        if (rows.isEmpty()) {
            rows = table.select("tr:not(:has(th))");
        }
        return new ArrayList<>(rows);
    }

    static String phaseFor(Element table) {
        Element container = table.closest("[id]");
        if (container != null) {
            String id = container.id();
            Element documentRoot = table.ownerDocument();
            Element tab = documentRoot.selectFirst("a[href='#" + id.replace("'", "\\'") + "']");
            if (tab != null && !clean(tab.text()).isBlank()) {
                return clean(tab.text());
            }
            String ariaLabel = clean(container.attr("aria-label"));
            if (ariaLabel != null && !ariaLabel.isBlank()) {
                return ariaLabel;
            }
        }

        Element previous = table.previousElementSibling();
        while (previous != null) {
            if (previous.is("h1,h2,h3,h4,h5,h6,.card-header,.panel-heading")) {
                String text = clean(previous.text());
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
            previous = previous.previousElementSibling();
        }
        return "Geral";
    }

    static String cell(Elements cells, int index) {
        return index >= 0 && index < cells.size() ? clean(cells.get(index).text()) : null;
    }

    static Element cellElement(Elements cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : null;
    }

    static int indexOf(Map<String, Integer> headers, String... candidates) {
        for (Map.Entry<String, Integer> entry : headers.entrySet()) {
            for (String candidate : candidates) {
                String normalizedCandidate = normalized(candidate);
                if (entry.getKey().equals(normalizedCandidate) || entry.getKey().contains(normalizedCandidate)) {
                    return entry.getValue();
                }
            }
        }
        return -1;
    }

    static Long queryLong(String url, String parameter) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            String query = URI.create(url).getQuery();
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2 && parts[0].equals(parameter)) {
                    return Long.valueOf(parts[1]);
                }
            }
        } catch (RuntimeException ignored) {
            // URL externa inesperada: o parser devolve null, sem derrubar toda a resposta.
        }
        return null;
    }

    static String valueAfterLabel(String text, String label) {
        if (text == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(label) + "\\s*:\\s*(.+)");
        Matcher matcher = pattern.matcher(clean(text));
        return matcher.find() ? clean(matcher.group(1)) : null;
    }
}
