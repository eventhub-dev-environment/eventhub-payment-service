package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.exception.PaymentConflictException;
import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;
import com.eventhub.payment_service.domain.port.in.GetPaymentUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.domain.port.out.PaymentWebhookReceiptRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements ProcessWebhookUseCase, GetPaymentUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentEventPublisherPort paymentEventPublisherPort;
    private final PaymentWebhookReceiptRepositoryPort receiptRepositoryPort;

    @Value("${payment.currency:COP}")
    private String configuredCurrency;

    @Override
    @Transactional
    public Payment processWebhook(String bookingId, String transactionId, String idempotencyKey,
                                  PaymentProvider provider, BigDecimal amount, String currency,
                                  PaymentStatus status, String payloadHash) {

        validateWebhook(bookingId, transactionId, idempotencyKey, provider, amount, currency, status, payloadHash);

        Optional<PaymentWebhookReceipt> existingReceipt =
                receiptRepositoryPort.findByProviderAndIdempotencyKey(provider, idempotencyKey);
        if (existingReceipt.isPresent()) {
            PaymentWebhookReceipt receipt = existingReceipt.get();
            if (!receipt.payloadHash().equals(payloadHash)) {
                throw new PaymentConflictException("La clave idempotente ya fue usada con otro contenido");
            }
            return paymentRepositoryPort.findById(receipt.paymentId())
                    .orElseThrow(() -> new PaymentConflictException(
                            "El registro idempotente no tiene un pago asociado"));
        }

        Optional<Payment> transactionPayment =
                paymentRepositoryPort.findByProviderAndTransactionIdForUpdate(provider, transactionId);
        if (transactionPayment.isPresent()
                && !transactionPayment.get().getBookingId().equals(bookingId)) {
            throw new PaymentConflictException("La transacción ya está asociada a otro pago");
        }

        Optional<Payment> bookingPayment = paymentRepositoryPort.findByBookingIdForUpdate(bookingId);
        Payment payment = bookingPayment.orElseGet(() -> Payment.builder()
                .bookingId(bookingId)
                .provider(provider)
                .amount(amount)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .build());

        if (bookingPayment.isPresent()) {
            ensureSamePaymentIdentity(payment, transactionId, provider, amount, currency);
            if (payment.getStatus() == PaymentStatus.APPROVED
                    || payment.getStatus() == status) {
                saveReceipt(provider, idempotencyKey, transactionId, bookingId, payloadHash, payment);
                return payment;
            }
        }

        payment.setTransactionId(transactionId);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProvider(provider);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(status);
        payment.setRawPayload(null);
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepositoryPort.save(payment);
        saveReceipt(provider, idempotencyKey, transactionId, bookingId, payloadHash, savedPayment);

        if (status == PaymentStatus.APPROVED) {
            PaymentCompletedEvent completedEvent = PaymentCompletedEvent.builder()
                    .paymentId(savedPayment.getId())
                    .bookingId(savedPayment.getBookingId())
                    .transactionId(savedPayment.getTransactionId())
                    .amount(savedPayment.getAmount())
                    .currency(savedPayment.getCurrency())
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

    private void validateWebhook(
            String bookingId,
            String transactionId,
            String idempotencyKey,
            PaymentProvider provider,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            String payloadHash) {
        if (bookingId == null || bookingId.isBlank() || transactionId == null || transactionId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank() || provider == null || amount == null
                || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 8 || currency == null
                || !currency.equals(configuredCurrency) || payloadHash == null || payloadHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El evento de pago contiene datos inválidos");
        }
        if (status != PaymentStatus.APPROVED && status != PaymentStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El webhook solo puede informar pagos aprobados o rechazados");
        }
    }

    private void ensureSamePaymentIdentity(
            Payment payment,
            String transactionId,
            PaymentProvider provider,
            BigDecimal amount,
            String currency) {
        if (!provider.equals(payment.getProvider())
                || amount.compareTo(payment.getAmount()) != 0
                || !currency.equals(payment.getCurrency())) {
            throw new PaymentConflictException("El proveedor, importe o moneda no coincide con el pago registrado");
        }
        if (!transactionId.equals(payment.getTransactionId())) {
            throw new PaymentConflictException("La reserva ya tiene otra transacción de pago activa");
        }
    }

    private void saveReceipt(
            PaymentProvider provider,
            String idempotencyKey,
            String transactionId,
            String bookingId,
            String payloadHash,
            Payment payment) {
        receiptRepositoryPort.save(new PaymentWebhookReceipt(
                null,
                provider,
                idempotencyKey,
                transactionId,
                bookingId,
                payloadHash,
                payment.getId(),
                LocalDateTime.now()));
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