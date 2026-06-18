package br.edu.infnet.order.handler;

import br.edu.infnet.order.exception.OrderNotFoundException;
import br.edu.infnet.order.integration.product.exception.ProductNotFoundException;
import br.edu.infnet.order.integration.product.exception.ProductServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleOrderNotFoundException(OrderNotFoundException ex){
        Map<String, Object> body = Map.of("timestamp", LocalDateTime.now(),
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage()
                );
        return body;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex){
        Map<String, Object> body = Map.of("timestamp", LocalDateTime.now(),
                "status", 400,
                "error", "Bad Request",
                "message", ex.getMessage()
        );
        return body;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> handleProductNotFoundException(ProductNotFoundException ex) {
        return Map.of(
                "timestamp", LocalDateTime.now(),
                "status", 422,
                "error", "Unprocessable Entity",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(HttpClientErrorException.class)
    @ResponseStatus(HttpStatus.FAILED_DEPENDENCY)
    public Map<String, Object> handleHttpClientError(HttpClientErrorException ex) {
        return Map.of(
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(HttpTimeoutException.class)
    @ResponseStatus(HttpStatus.FAILED_DEPENDENCY)
    public Map<String, String> handlerTimeout(HttpTimeoutException ex) {
        return Map.of(
                "message", "Sistema temporariamente indisponível.");
    }

    /**
     * FALLBACK / degradação graciosa: chega aqui quando todas as tentativas de
     * retry ao Product-Service falharam (timeout, conexão recusada ou erro 5xx).
     * Em vez de devolver um stacktrace, respondemos 503 (Service Unavailable) com
     * uma mensagem clara, sinalizando ao cliente que pode tentar novamente mais tarde.
     */
    @ExceptionHandler({ProductServiceUnavailableException.class, ResourceAccessException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleProductUnavailable(RuntimeException ex) {
        return Map.of(
                "timestamp", LocalDateTime.now(),
                "status", 503,
                "error", "Service Unavailable",
                "message", "Serviço de produtos indisponível no momento. Tente novamente em instantes."
        );
    }


}
