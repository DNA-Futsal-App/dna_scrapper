package br.com.dnafutsal.scraper.exception;

public class UpstreamAccessException extends RuntimeException {
    public UpstreamAccessException(String message) {
        super(message);
    }

    public UpstreamAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
