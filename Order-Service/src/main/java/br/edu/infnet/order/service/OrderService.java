package br.edu.infnet.order.service;

import br.edu.infnet.order.domain.enums.OrderStatus;
import br.edu.infnet.order.domain.model.Order;
import br.edu.infnet.order.domain.model.OrderItem;
import br.edu.infnet.order.dto.CreateOrderRequest;
import br.edu.infnet.order.dto.OrderItemDTO;
import br.edu.infnet.order.dto.OrderResponse;
import br.edu.infnet.order.integration.product.client.ProductClient;
import br.edu.infnet.order.integration.product.dto.ProductResponse;
import br.edu.infnet.order.messaging.event.PedidoCriadoEvent;
import br.edu.infnet.order.messaging.producer.OrderEventPublisher;
import br.edu.infnet.order.repository.OrderRepository;
import br.edu.infnet.order.exception.OrderNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient  productClient;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient, OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderEventPublisher = orderEventPublisher;
    }

    public OrderResponse create(CreateOrderRequest request) {
        Order o = new Order();
        o.setCustomerName(request.getCustomerName());
        o.setPaymentMethod(request.getPaymentMethod());

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemDTO item : request.getItems()) {
            ProductResponse p = productClient.getProductById(item.productId());

            OrderItem oi = new OrderItem();
            oi.setProductId(p.id());
            oi.setQuantity(item.quantity());
            oi.setUnitPrice(p.price());
            items.add(oi);

            total = total.add(
                    BigDecimal.valueOf(oi.getQuantity())
                            .multiply(oi.getUnitPrice())
            );
        }

        o.setItems(items);
        o.setOrderDate(LocalDateTime.now());
        o.setOrderStatus(OrderStatus.PENDING);
        o.setTotalAmount(total);

        //Retira os itens do stock (validação síncrona "tudo ou nada")
        productClient.reduceProductQuantityStock(request.getItems());

        // Persiste o pedido como PENDING para obter o ID gerado.
        orderRepository.save(o);

        // COMUNICAÇÃO ASSÍNCRONA: publica o evento de domínio PedidoCriado.
        // O Payment-Service consome esse evento e processa o pagamento de forma
        // desacoplada. Se o Payment estiver indisponível, a mensagem aguarda na
        // fila e o pedido não é bloqueado. Quando o pagamento for processado, o
        // Payment publica PagamentoProcessado, consumido aqui para atualizar o status.
        orderEventPublisher.publishPedidoCriado(new PedidoCriadoEvent(
                UUID.randomUUID(),
                o.getId(),
                o.getCustomerName(),
                o.getTotalAmount(),
                o.getPaymentMethod()
        ));

        return toResponse(o);
    }

    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));
        return toResponse(order);
    }

    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    private OrderResponse toResponse(Order o){
        return new OrderResponse(
                o.getId(),
                o.getCustomerName(),
                o.getOrderDate(),
                o.getOrderStatus(),
                o.getTotalAmount(),
                o.getItems()
        );
    }

}
