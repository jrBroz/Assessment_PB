package br.edu.infnet.order.integration.product.client;

import br.edu.infnet.order.dto.OrderItemDTO;
import br.edu.infnet.order.integration.product.exception.ProductNotFoundException;
import br.edu.infnet.order.integration.product.exception.ProductServiceUnavailableException;
import br.edu.infnet.order.integration.product.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Cliente HTTP (síncrono) para o Product-Service, com tratamento de erros.
 *
 * <p><b>Timeout:</b> configurado no {@link br.edu.infnet.order.config.ProductClientConfig}.
 *
 * <p><b>Retry:</b> os métodos abaixo são anotados com {@code @Retryable} apenas para falhas
 * TRANSITÓRIAS — {@link ResourceAccessException} (timeout/conexão recusada) e
 * {@link ProductServiceUnavailableException} (erro 5xx). Erros de NEGÓCIO, como
 * {@link ProductNotFoundException} (404), NÃO são retentados, pois repetir não muda o resultado.
 *
 * <p><b>Fallback:</b> esgotadas as tentativas, a exceção transitória propaga e é traduzida
 * para HTTP 503 pelo GlobalExceptionHandler (degradação graciosa em vez de stacktrace).
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    private final RestClient ProductRestClient;

    public ProductClient(@Qualifier("productRestClient") RestClient ProductRestClient) {
        this.ProductRestClient = ProductRestClient;
    }

    @Retryable(
            includes = {ResourceAccessException.class, ProductServiceUnavailableException.class},
            maxRetries = 3,
            delay = 500,
            multiplier = 2.0,
            maxDelay = 3000,
            jitter = 200
    )
    public ProductResponse getProductById(String productId) {
        log.info("Consultando produto {} no Product-Service", productId);
        return ProductRestClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    // Erro de negócio: produto não existe. Não deve ser retentado.
                    throw new ProductNotFoundException(
                            "O produto com ID '" + productId + "' não foi encontrado no catálogo.");
                })
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError, (request, response) -> {
                    // Erro transitório do servidor remoto. Será retentado.
                    throw new ProductServiceUnavailableException(
                            "Product-Service retornou erro " + response.getStatusCode() + " ao consultar o produto.");
                })
                .body(ProductResponse.class);
    }

    @Retryable(
            includes = {ResourceAccessException.class, ProductServiceUnavailableException.class},
            maxRetries = 3,
            delay = 500,
            multiplier = 2.0,
            maxDelay = 3000,
            jitter = 200
    )
    public void reduceProductQuantityStock(List<OrderItemDTO> orderItemDTOs) {
        log.info("Reduzindo estoque de {} item(ns) no Product-Service", orderItemDTOs.size());
        ProductRestClient.patch()
                .uri("/products/update-stock")
                .body(orderItemDTOs)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new ProductServiceUnavailableException(
                            "Product-Service retornou erro " + response.getStatusCode() + " ao baixar o estoque.");
                })
                .onStatus(org.springframework.http.HttpStatusCode::is4xxClientError, (request, response) -> {
                    // Ex.: estoque insuficiente ou produto inexistente. Erro de negócio: não retenta.
                    String body = new String(response.getBody().readAllBytes());
                    throw new ProductNotFoundException("Falha ao baixar estoque no Product-Service: " + body);
                })
                .toBodilessEntity();
    }
}
