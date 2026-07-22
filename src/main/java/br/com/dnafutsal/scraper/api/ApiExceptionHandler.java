package br.com.dnafutsal.scraper.api;

import br.com.dnafutsal.scraper.exception.ResourceNotFoundException;
import br.com.dnafutsal.scraper.exception.ScrapingParseException;
import br.com.dnafutsal.scraper.exception.UpstreamAccessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado", exception.getMessage(), request);
    }

    @ExceptionHandler({UpstreamAccessException.class, ScrapingParseException.class})
    ProblemDetail upstream(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_GATEWAY, "Falha na fonte externa", exception.getMessage(), request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class,
            TypeMismatchException.class
    })
    ProblemDetail badRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", safeMessage(exception), request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail internal(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "A requisição não pôde ser concluída", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.dnafutsal.com.br/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "A requisição contém parâmetros inválidos" : message;
    }
}
