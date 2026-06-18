package br.edu.infnet.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ProductClientConfig {

    /**
     * TIMEOUT: limita o tempo de espera ao chamar o Product-Service.
     * - connectTimeout: tempo máximo para abrir a conexão TCP (no HttpClient).
     * - readTimeout: tempo máximo aguardando a resposta depois de conectado.
     * Sem isso, uma chamada poderia ficar pendurada indefinidamente se o Product
     * travasse, segurando a thread do pedido. Estourado o tempo, é lançada uma
     * ResourceAccessException — tratada como falha transitória (retry).
     *
     * Usamos a JdkClientHttpRequestFactory (java.net.http.HttpClient) porque ela
     * suporta o método HTTP PATCH (necessário para o /products/update-stock); a
     * SimpleClientHttpRequestFactory, baseada em HttpURLConnection, não suporta PATCH.
     */
    @Bean
    public RestClient productRestClient(
            @Value("${integration.product.base-url}")
            String baseUrl
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
