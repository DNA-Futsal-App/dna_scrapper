package br.com.dnafutsal.scraper.exception;

public class ScrapingParseException extends RuntimeException {
    public ScrapingParseException(String message) {
        super(message);
    }

    public ScrapingParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
