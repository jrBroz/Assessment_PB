package br.edu.infnet.order.integration.product.exception;

/**
 * Erro TRANSITÓRIO na comunicação com o Product-Service (timeout, conexão recusada,
 * indisponibilidade ou erro 5xx).
 *
 * <p>É diferente de {@link ProductNotFoundException}, que representa um erro de NEGÓCIO
 * (produto inexistente, 404). Apenas este tipo transitório é alvo de retry e, depois de
 * esgotadas as tentativas, é traduzido para HTTP 503 (Service Unavailable).
 */
public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message) {
        super(message);
    }
}
