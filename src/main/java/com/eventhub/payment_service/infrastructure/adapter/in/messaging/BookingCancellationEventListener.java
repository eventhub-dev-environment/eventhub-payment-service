package com.eventhub.payment_service.infrastructure.adapter.in.messaging;

import com.eventhub.payment_service.domain.port.in.RefundPaymentUseCase;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.SpringDataPaymentRefundInboxRepository;
import com.eventhub.payment_service.infrastructure.adapter.out.persistence.entity.PaymentRefundInboxEventEntity;
import com.eventhub.payment_service.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCancellationEventListener {

    private final RefundPaymentUseCase refundPaymentUseCase;
    private final SpringDataPaymentRefundInboxRepository inboxRepository;

    @RabbitListener(queues = RabbitMQConfig.BOOKING_CANCELLATION_QUEUE)
    @Transactional
    public void handleBookingCancellation(
            Map<String, Object> event,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        String bookingId = text(event.get("bookingId"));
        String transactionId = text(event.get("transactionId"));
        String eventId = messageId == null || messageId.isBlank()
                ? "legacy:booking.cancellation:" + bookingId + ":" + transactionId
                : messageId;
        if (inboxRepository.existsById(eventId)) {
            log.info("Se ignora solicitud de reembolso ya procesada: {}", eventId);
            return;
        }
        BigDecimal amount = amount(event.get("amount"));
        String currency = text(event.get("currency"));
        if (bookingId == null || transactionId == null || amount == null
                || amount.signum() <= 0 || currency == null) {
            throw new IllegalArgumentException("Solicitud de reembolso inválida");
        }

        refundPaymentUseCase.refundBooking(bookingId, transactionId, amount, currency);
        inboxRepository.save(PaymentRefundInboxEventEntity.builder()
                .eventId(eventId)
                .bookingId(bookingId)
                .processedAt(LocalDateTime.now())
                .build());
    }

    private String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private BigDecimal amount(Object value) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return null;
    }
}
