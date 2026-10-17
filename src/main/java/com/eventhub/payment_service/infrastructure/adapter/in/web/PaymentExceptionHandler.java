package com.eventhub.payment_service.infrastructure.adapter.in.web;

import com.eventhub.payment_service.domain.exception.PaymentConflictException;
import com.eventhub.payment_service.infrastructure.adapter.in.web.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ApiResponse.error(exception.getReason(), null));
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentConflict(PaymentConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(exception.getMessage(), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(DataIntegrityViolationException exception) {
        log.warn("Se rechazó un webhook duplicado o conflictivo por una restricción de unicidad");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("El webhook entra en conflicto con un pago ya registrado", null));
    }
}
