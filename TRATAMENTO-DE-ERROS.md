# Tratamento de Erros na Comunicação entre Microsserviços

Este documento descreve os erros possíveis na comunicação entre os microsserviços do
sistema, as estratégias de tratamento de erros aplicadas e como testar cada falha.

## 1. Visão geral da comunicação

O sistema possui dois tipos de comunicação entre serviços:

| Tipo | Onde | Tecnologia |
|------|------|------------|
| **Síncrona** | `Order-Service` → `Product-Service` (consultar produto e baixar estoque) | HTTP (`RestClient`) |
| **Assíncrona** | `Order-Service` ↔ `Payment-Service` (fluxo de pagamento) | Mensageria (RabbitMQ) |

Cada tipo de comunicação está sujeito a falhas diferentes e usa estratégias diferentes.

---

## 2. Erros possíveis e estratégias aplicadas

### 2.1. Comunicação SÍNCRONA (Order → Product)

Por ser uma chamada HTTP bloqueante, o `Order-Service` fica dependente da disponibilidade
e do tempo de resposta do `Product-Service`.

| Erro possível | Causa | Estratégia aplicada |
|---------------|-------|---------------------|
| **Tempo de resposta excessivo** | Product lento/travado | **Timeout** (2s de conexão + 2s de leitura) |
| **Falha transitória** (timeout, conexão recusada, erro 5xx) | Instabilidade de rede ou Product reiniciando | **Retry** (até 3 tentativas, backoff exponencial) |
| **Indisponibilidade total** após esgotar tentativas | Product fora do ar | **Fallback**: resposta HTTP **503** com mensagem amigável |
| **Erro de negócio** (produto inexistente = 404, estoque insuficiente) | Dado inválido | **NÃO** é retentado; vira erro de negócio (422) com a mensagem real |

**Onde está no código:**

- **Timeout** — [`ProductClientConfig.java`](Order-Service/src/main/java/br/edu/infnet/order/config/ProductClientConfig.java): `SimpleClientHttpRequestFactory` com `connectTimeout` e `readTimeout` de 2 segundos.
- **Retry** — [`ProductClient.java`](Order-Service/src/main/java/br/edu/infnet/order/integration/product/client/ProductClient.java): anotação `@Retryable` (nativa do Spring Framework 7) **apenas** para `ResourceAccessException` (timeout/conexão) e `ProductServiceUnavailableException` (5xx). Habilitada por `@EnableResilientMethods` em [`ResilienceConfig.java`](Order-Service/src/main/java/br/edu/infnet/order/config/ResilienceConfig.java).
- **Fallback** — [`GlobalExceptionHandler.java`](Order-Service/src/main/java/br/edu/infnet/order/handler/GlobalExceptionHandler.java): traduz a falha transitória esgotada em HTTP **503 Service Unavailable**.
- **Classificação de erros** — distinguimos *falha transitória* (vale a pena tentar de novo) de *erro de negócio* (404/estoque — repetir não muda nada), evitando retries inúteis.

> **Sobre Circuit Breaker:** é um mecanismo complementar válido (abrir o circuito após
> N falhas para parar de tentar e dar tempo ao serviço se recuperar). Optamos por **não**
> implementá-lo porque o starter do Spring Cloud Circuit Breaker 5.0.0 ainda tem
> incompatibilidades conhecidas com o Spring Boot 4, e timeout + retry + fallback já
> cobrem os cenários de falha deste sistema. Fica registrado como evolução futura.

### 2.2. Comunicação ASSÍNCRONA (Order ↔ Payment via RabbitMQ)

Na mensageria o produtor não espera o consumidor, então os problemas são diferentes.

| Erro possível | Causa | Estratégia aplicada |
|---------------|-------|---------------------|
| **Consumidor indisponível** | Payment (ou Order) fora do ar | **Filas duráveis + mensagens persistentes**: a mensagem aguarda na fila e é entregue quando o serviço volta |
| **Falha ao processar a mensagem** | Erro temporário no consumidor | **Retry** (3 tentativas com backoff) configurado no listener |
| **Falha persistente** após o retry | Erro irrecuperável ("poison message") | **Dead Letter Queue (DLQ)**: a mensagem é desviada para análise, sem travar a fila |
| **Mensagem entregue mais de uma vez** | Garantia *at-least-once* do RabbitMQ | **Idempotência**: tabela `processed_events` com o `eventId`; duplicatas são ignoradas |

**Onde está no código:**

- **Durabilidade + DLQ** — `RabbitMQConfig.java` (em ambos os serviços): exchange e filas declarados como duráveis; cada fila tem `x-dead-letter-exchange` apontando para a DLQ.
- **Retry** — `application.yaml` (ambos): `spring.rabbitmq.listener.simple.retry` com `max-attempts: 3` e backoff; `default-requeue-rejected: false` envia para a DLQ após esgotar.
- **Idempotência** — entidade `ProcessedEvent` + verificação no início de cada listener (`PedidoCriadoListener`, `PagamentoProcessadoListener`).

---

## 3. Como testar as falhas

> Pré-requisitos: `docker compose up -d` (RabbitMQ) e os serviços rodando.

### 3.1. Timeout (síncrono)
1. Deixe o `Product-Service` artificialmente lento (ex.: adicione um `Thread.sleep(5000)` temporário no `ProductController.findById`, acima dos 2s de timeout).
2. Crie um pedido (`POST /orders`).
3. **Esperado:** após ~2s por tentativa, o Order desiste e responde **503**. No log do Order aparecem as tentativas de retry.

### 3.2. Retry + Fallback (síncrono — Product indisponível)
1. **Derrube o `Product-Service`** (pare o processo).
2. Crie um pedido (`POST /orders`).
3. **Esperado:** o Order tenta 3 vezes (visível no log: "Consultando produto..." repetido com intervalos crescentes) e, ao falhar todas, responde **503 Service Unavailable** com a mensagem amigável — em vez de um stacktrace.

### 3.3. Erro de negócio NÃO é retentado (síncrono)
1. Com o `Product-Service` no ar, crie um pedido com um `productId` inexistente.
2. **Esperado:** resposta **422** imediata (sem retry), pois 404 é erro de negócio.

### 3.4. Consumidor indisponível (assíncrono)
1. **Derrube o `Payment-Service`.**
2. Crie um pedido válido. Ele é salvo como `PENDING` e a resposta volta na hora.
3. Verifique a fila: `docker exec rabbitmq rabbitmqctl list_queues name messages consumers`
   → `payment.pedido-criado.queue` terá **1 mensagem, 0 consumidores** (mensagem retida).
4. **Suba o `Payment-Service`.** Ele consome a mensagem atrasada e o pedido vira `CONFIRMED`.

### 3.5. Mensagem duplicada / idempotência (assíncrono)
1. Pela Management UI do RabbitMQ (`http://localhost:15672`, guest/guest), publique no
   exchange `ecommerce.exchange`, routing key `pedido.criado`, **duas vezes** o mesmo JSON
   (mesmo `eventId`), com `content_type = application/json`. Exemplo de payload:
   ```json
   { "eventId": "11111111-1111-1111-1111-111111111111",
     "orderId": "22222222-2222-2222-2222-222222222222",
     "customerName": "Teste", "amount": 150, "paymentMethod": "PIX" }
   ```
2. **Esperado:** apenas **1 pagamento** é criado. No log do Payment, a segunda mensagem
   gera: *"Evento ... já processado anteriormente. Ignorando duplicata."*

---

## 4. Resumo dos mecanismos

| Mecanismo | Comunicação | Implementado? |
|-----------|-------------|:---:|
| Timeout | Síncrona | ✅ |
| Retry | Síncrona e Assíncrona | ✅ |
| Fallback (degradação graciosa / 503) | Síncrona | ✅ |
| Dead Letter Queue | Assíncrona | ✅ |
| Idempotência | Assíncrona | ✅ |
| Circuit Breaker | Síncrona | ⚠️ Considerado (evolução futura — ver nota em 2.1) |
