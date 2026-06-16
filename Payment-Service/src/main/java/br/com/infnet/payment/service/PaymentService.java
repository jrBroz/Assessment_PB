package br.com.infnet.payment.service;

import br.com.infnet.payment.domain.enums.PaymentMethod;
import br.com.infnet.payment.domain.enums.PaymentStatus;
import br.com.infnet.payment.domain.model.Payment;
import br.com.infnet.payment.dto.PaymentRequest;
import br.com.infnet.payment.dto.PaymentResponse;
import br.com.infnet.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public PaymentResponse create(PaymentRequest order) {
        Payment payment = new Payment();
        payment.setOrderId(order.orderId());
        payment.setAmount(order.amount());
        payment.setPaymentMethod(order.paymentMethod());

        payment.setStatus(PaymentStatus.PENDING); //o que fazer aqui?
        //Acho que payment deve ter um client de order para avisar o status do pagamento

        Payment savedPayment = paymentRepository.save(payment);
        return toResponse(savedPayment);
    }

    /**
     * Processa o pagamento de um pedido recebido via evento {@code PedidoCriado}.
     *
     * <p>Aplica uma regra de negócio simulada (um gateway real seria chamado aqui):
     * pagamentos de até R$ 5.000 são aprovados; acima disso, recusados — apenas para
     * demonstrar os dois caminhos (APPROVED / REJECTED) no fluxo assíncrono.
     *
     * @return o pagamento já persistido com o status decidido.
     */
    public Payment processPayment(UUID orderId, BigDecimal amount, PaymentMethod paymentMethod) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setPaymentMethod(paymentMethod);

        boolean aprovado = amount != null && amount.compareTo(new BigDecimal("5000")) <= 0;
        payment.setStatus(aprovado ? PaymentStatus.APPROVED : PaymentStatus.REJECTED);

        return paymentRepository.save(payment);
    }

    public PaymentResponse getById(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado para o ID: " + id));
        return toResponse(payment);
    }

    public List<PaymentResponse> getAll() {
        List<Payment> payments = paymentRepository.findAll();
        return payments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PaymentResponse updateStatus(UUID id, PaymentStatus newStatus) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pagamento não encontrado para o ID: " + id));

        payment.setStatus(newStatus);

        Payment updatedPayment = paymentRepository.save(payment);
        return toResponse(updatedPayment);
    }

    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getOrderId(),
                p.getAmount(),
                p.getStatus(),
                p.getPaymentMethod(),
                p.getCreatedAt()
        );
    }
}
