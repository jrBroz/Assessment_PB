package br.edu.infnet.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Habilita o processamento das anotações de resiliência nativas do Spring Framework 7
 * (Spring Boot 4), em especial {@code @Retryable}. Sem esta anotação, o {@code @Retryable}
 * colocado no {@code ProductClient} seria ignorado.
 */
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}
