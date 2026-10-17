package com.eventhub.payment_service.application.usecase;

import com.eventhub.payment_service.domain.exception.PaymentConflictException;
import com.eventhub.payment_service.domain.event.PaymentCompletedEvent;
import com.eventhub.payment_service.domain.event.PaymentFailedEvent;
import com.eventhub.payment_service.domain.event.PaymentRefundFailedEvent;
import com.eventhub.payment_service.domain.event.PaymentRefundedEvent;
import com.eventhub.payment_service.domain.model.Payment;
import com.eventhub.payment_service.domain.model.PaymentProvider;
import com.eventhub.payment_service.domain.model.PaymentStatus;
import com.eventhub.payment_service.domain.model.CheckoutAttempt;
import com.eventhub.payment_service.domain.model.CheckoutAttemptStatus;
import com.eventhub.payment_service.domain.model.StripeRefundResult;
import com.eventhub.payment_service.domain.model.PaymentWebhookReceipt;
import com.eventhub.payment_service.domain.port.in.GetPaymentUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessStripeRefundUpdateUseCase;
import com.eventhub.payment_service.domain.port.in.ProcessWebhookUseCase;
import com.eventhub.payment_service.domain.port.in.RefundPaymentUseCase;
import com.eventhub.payment_service.domain.port.out.PaymentEventPublisherPort;
import com.eventhub.payment_service.domain.port.out.PaymentRepositoryPort;
import com.eventhub.payment_service.domain.port.out.CheckoutAttemptRepositoryPort;
import com.eventhub.payment_service.domain.port.out.StripeRefundPort;
import com.eventhub.payment_service.domain.port.out.PaymentWebhookReceiptRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements ProcessWebhookUseCase, GetPaymentUseCase, RefundPaymentUseCase,
        ProcessStripeRefundUpdateUseCase {

    private final PaymentRepositoryPort paymentRepositoryPort;
    private final PaymentEventPublisherPort paymentEventPublisherPort;
    private final PaymentWebhookReceiptRepositoryPort receiptRepositoryPort;
    private final StripeRefundPort stripeRefundPort;
    private final CheckoutAttemptRepositoryPort checkoutAttemptRepositoryPort;

    @Override
    @Transactional
    public Payment processWebhook(String bookingId, String transactionId, String idempotencyKey,
                                  PaymentProvider provider, BigDecimal amount, String currency,
                                  PaymentStatus status, String payloadHash) {
        return processWebhook(bookingId, null, transactionId, idempotencyKey,
                provider, amount, currency, status, payloadHash);
    }

    @Override
    @Transactional
    public Payment processWebhook(String bookingId, String attemptId, String transactionId, String idempotencyKey,
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

        Optional<CheckoutAttempt> checkoutAttempt = Optional.empty();
        if (attemptId != null && !attemptId.isBlank()) {
            checkoutAttempt = checkoutAttemptRepositoryPort.findByIdForUpdate(attemptId);
            if (checkoutAttempt.isEmpty() || !bookingId.equals(checkoutAttempt.get().getBookingId())) {
                throw new PaymentConflictException("El intento de Checkout no coincide con la reserva");
            }
        }

        Optional<Payment> transactionPayment =
                paymentRepositoryPort.findByProviderAndTransactionIdForUpdate(provider, transactionId);
        if (transactionPayment.isPresent()
                && !transactionPayment.get().getBookingId().equals(bookingId)) {
            throw new PaymentConflictException("La transacción ya está asociada a otro pago");
        }

        Optional<Payment> attemptPayment = transactionPayment.isPresent()
                ? transactionPayment
                : checkoutAttempt.isPresent()
                    ? paymentRepositoryPort.findByCheckoutAttemptIdForUpdate(attemptId)
                    : paymentRepositoryPort.findByBookingIdForUpdate(bookingId);
        if (attemptPayment.isPresent()
                && attemptId != null && !attemptId.equals(attemptPayment.get().getCheckoutAttemptId())) {
            throw new PaymentConflictException("El pago está asociado a otro intento de Checkout");
        }
        Payment payment = attemptPayment.orElseGet(() -> Payment.builder()
                .bookingId(bookingId)
                .checkoutAttemptId(attemptId)
                .provider(provider)
                .amount(amount)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .build());

        if (attemptPayment.isPresent()) {
            ensureSamePaymentIdentity(payment, transactionId, provider, amount, currency);
            if (payment.getStatus() == PaymentStatus.APPROVED
                    || payment.getStatus() == PaymentStatus.REFUND_PENDING
                    || payment.getStatus() == PaymentStatus.REFUND_FAILED
                    || payment.getStatus() == PaymentStatus.REFUNDED
                    || payment.getStatus() == status) {
                saveReceipt(provider, idempotencyKey, transactionId, bookingId, payloadHash, payment);
                return payment;
            }
        }

        payment.setTransactionId(transactionId);
        if (attemptId != null && !attemptId.isBlank()) {
            payment.setCheckoutAttemptId(attemptId);
        }
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProvider(provider);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(status);
        payment.setRawPayload(null);
        payment.setUpdatedAt(LocalDateTime.now());

        Payment savedPayment = paymentRepositoryPort.save(payment);
        saveReceipt(provider, idempotencyKey, transactionId, bookingId, payloadHash, savedPayment);
        if (checkoutAttempt.isPresent()) {
            CheckoutAttempt attempt = checkoutAttempt.get();
            if (status == PaymentStatus.APPROVED) {
                attempt.setStatus(CheckoutAttemptStatus.COMPLETED);
            } else if (attempt.getStatus() != CheckoutAttemptStatus.COMPLETED
                    && attempt.getStatus() != CheckoutAttemptStatus.CLOSED) {
                attempt.setStatus(CheckoutAttemptStatus.CLOSING);
            }
            attempt.setUpdatedAt(LocalDateTime.now());
            checkoutAttemptRepositoryPort.save(attempt);
        }
        if (status == PaymentStatus.APPROVED) {
            checkoutAttemptRepositoryPort.findActiveByBookingIdForUpdate(bookingId)
                    .filter(activeAttempt -> !activeAttempt.getId().equals(attemptId))
                    .ifPresent(activeAttempt -> {
                        activeAttempt.setStatus(CheckoutAttemptStatus.CLOSING);
                        activeAttempt.setUpdatedAt(LocalDateTime.now());
                        checkoutAttemptRepositoryPort.save(activeAttempt);
                    });
        }

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
                || !currency.matches("[A-Z]{3}") || payloadHash == null || payloadHash.isBlank()) {
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
        if (payment.getTransactionId() != null && !transactionId.equals(payment.getTransactionId())) {
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
    @Transactional
    public void refundBooking(String bookingId, String transactionId, BigDecimal amount, String currency) {
        if (bookingId == null || bookingId.isBlank() || transactionId == null || transactionId.isBlank()
                || amount == null || currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("La solicitud de reembolso contiene datos inválidos");
        }
        Optional<Payment> transactionPayment = paymentRepositoryPort.findByProviderAndTransactionIdForUpdate(
                PaymentProvider.STRIPE, transactionId);
        if (transactionPayment.isPresent() && !bookingId.equals(transactionPayment.get().getBookingId())) {
            throw new PaymentConflictException("La transacción de reembolso pertenece a otra reserva");
        }
        Payment payment = transactionPayment.orElseGet(() -> paymentRepositoryPort.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new PaymentConflictException("No existe un pago para la reserva")));
        if (!transactionId.equals(payment.getTransactionId())) {
            publishRefundFailure(payment, transactionId, "La transacción no coincide con el pago aprobado");
            return;
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.REFUND_PENDING
                || payment.getStatus() == PaymentStatus.REFUND_FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.APPROVED
                || payment.getAmount() == null
                || payment.getAmount().compareTo(amount) != 0
                || !currency.equals(payment.getCurrency())) {
            publishRefundFailure(payment, transactionId,
                    "El pago no coincide con la solicitud de reembolso");
            return;
        }
        if (payment.getProvider() != PaymentProvider.STRIPE) {
            publishRefundFailure(payment, transactionId,
                    "El proveedor del pago no admite reembolso automático");
            return;
        }

        StripeRefundResult refund = stripeRefundPort.refund(bookingId, transactionId);
        if (refund == null || refund.refundId() == null || refund.refundId().isBlank()
                || refund.status() == null || refund.status().isBlank()) {
            throw new IllegalStateException("Stripe devolvió un resultado de reembolso inválido");
        }
        payment.setRefundId(refund.refundId());
        switch (refund.status().toLowerCase(Locale.ROOT)) {
            case "succeeded" -> {
                payment.setStatus(PaymentStatus.REFUNDED);
                Payment savedPayment = paymentRepositoryPort.save(payment);
                publishRefunded(savedPayment);
            }
            case "pending" -> {
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                paymentRepositoryPort.save(payment);
            }
            case "failed", "canceled" -> {
                payment.setStatus(PaymentStatus.REFUND_FAILED);
                Payment savedPayment = paymentRepositoryPort.save(payment);
                publishRefundFailure(savedPayment, "Stripe no pudo completar el reembolso");
            }
            default -> throw new IllegalStateException("Stripe devolvió un estado de reembolso desconocido");
        }
    }

    @Override
    @Transactional
    public void processRefundUpdate(String refundId, String eventId, String payloadHash, String status) {
        if (refundId == null || refundId.isBlank() || eventId == null || eventId.isBlank()
                || payloadHash == null || payloadHash.isBlank() || status == null || status.isBlank()) {
            throw new IllegalArgumentException("El evento de reembolso de Stripe contiene datos inválidos");
        }
        Optional<PaymentWebhookReceipt> existingReceipt =
                receiptRepositoryPort.findByProviderAndIdempotencyKey(PaymentProvider.STRIPE, eventId);
        if (existingReceipt.isPresent()) {
            if (!existingReceipt.get().payloadHash().equals(payloadHash)) {
                throw new PaymentConflictException("El evento de Stripe ya fue recibido con otro contenido");
            }
            return;
        }

        Payment payment = paymentRepositoryPort.findByRefundIdForUpdate(refundId)
                .orElseThrow(() -> new PaymentConflictException("No existe un reembolso asociado al evento de Stripe"));
        String normalizedStatus = status.toLowerCase(Locale.ROOT);
        if (payment.getStatus() != PaymentStatus.REFUNDED && payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            switch (normalizedStatus) {
                case "succeeded" -> {
                    payment.setStatus(PaymentStatus.REFUNDED);
                    Payment savedPayment = paymentRepositoryPort.save(payment);
                    publishRefunded(savedPayment);
                }
                case "failed", "canceled" -> {
                    payment.setStatus(PaymentStatus.REFUND_FAILED);
                    Payment savedPayment = paymentRepositoryPort.save(payment);
                    publishRefundFailure(savedPayment, "Stripe no pudo completar el reembolso");
                }
                case "pending" -> paymentRepositoryPort.save(payment);
                default -> throw new IllegalArgumentException("Estado de reembolso de Stripe no reconocido");
            }
        }
        saveReceipt(PaymentProvider.STRIPE, eventId, payment.getTransactionId(), payment.getBookingId(),
                payloadHash, payment);
    }

    private void publishRefunded(Payment payment) {
        paymentEventPublisherPort.publishPaymentRefunded(PaymentRefundedEvent.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .transactionId(payment.getTransactionId())
                .refundId(payment.getRefundId())
                .refundedAt(LocalDateTime.now())
                .build());
    }

    private void publishRefundFailure(Payment payment, String reason) {
        publishRefundFailure(payment, payment.getTransactionId(), reason);
    }

    private void publishRefundFailure(Payment payment, String transactionId, String reason) {
        paymentEventPublisherPort.publishPaymentRefundFailed(PaymentRefundFailedEvent.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .transactionId(transactionId)
                .reason(reason)
                .failedAt(LocalDateTime.now())
                .build());
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