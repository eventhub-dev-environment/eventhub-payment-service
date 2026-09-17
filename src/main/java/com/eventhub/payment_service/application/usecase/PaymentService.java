package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.port.in.GetPaymentUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements ProcessWebhookUseCase, GetPaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentEventPublisherPort paymentEventPublisherPort;

    @Override
    public Payment processWebhook(String bookingId, String transactionId, PaymentProvider provider,
                                  BigDecimal amount, PaymentStatus status, String rawPayload) {

        log.info("Procesando webhook para bookingId: {}, transactionId: {}, status: {}", bookingId, transactionId, status);

        // 1. Control de Idempotencia: Verificar si ya existe este pago
        Optional<Payment> existingPayment = paymentRepositoryPort.findByTransactionId(transactionId);
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.APPROVED) {
            log.warn("El pago con transactionId: {} ya fue procesado anteriormente como APPROVED.", transactionId);
            return existingPayment.get();
        }

        // 2. Crear o actualizar el modelo Payment
        Payment payment = existingPayment.orElseGet(() -> Payment.builder()
                .bookingId(bookingId)
                .transactionId(transactionId)
                .provider(provider)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build());

        payment.setStatus(status);
        payment.setRawPayload(rawPayload);
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepositoryPort.save(payment);

        // 3. Publicación de Eventos Asíncronos
        if (status == PaymentStatus.APPROVED) {
            PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                    .paymentId(savedPayment.getId())
                    .bookingId(savedPayment.getBookingId())
                    .transactionId(savedPayment.getTransactionId())
                    .amount(savedPayment.getAmount())
                    .paymentProvider(savedPayment.getProvider().name())
                    .completedAt(savedPayment.getUpdatedAt())
                    .build();

            paymentEventPublisherPort.publishPaymentCompleted(completedEvent);
        } else if (status == PaymentStatus.REJECTED) {
            PaymentFailedEvent failedEvent = PaymentFailedEvent.builder()
                    .paymentId(savedPayment.getId())
                    .bookingId(savedPayment.getBookingId())
                    .reason("Pago rechazado por la pasarela " + provider.name())
                    .failedAt(savedPayment.getUpdatedAt())
                    .build();

            paymentEventPublisherPort.publishPaymentFailed(failedEvent);
        }

        return savedPayment;
    }

    @Override
    public Optional<Payment> getById(String id) {
        return paymentRepositoryPort.findById(id);
    }

    @Override
    public Optional<Payment> getByBookingId(String bookingId) {
        return paymentRepositoryPort.findByBookingId(bookingId);
    }

    @Override
    public Optional<Payment> getByTransactionId(String transactionId) {
        return paymentRepositoryPort.findByTransactionId(transactionId);
    }
}