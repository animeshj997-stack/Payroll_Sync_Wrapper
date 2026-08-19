package com.animesh.payroll.api;

import com.animesh.payroll.provider.ExternalPayrollException;
import com.animesh.payroll.provider.InvalidPayrollDataException;
import com.animesh.payroll.provider.UnknownPayrollProviderException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PayrollExceptionHandler {

    @ExceptionHandler(UnknownPayrollProviderException.class)
    ResponseEntity<ErrorResponse> handleUnknownProvider(UnknownPayrollProviderException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidPayrollDataException.class)
    ResponseEntity<ErrorResponse> handleInvalidData(InvalidPayrollDataException exception) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(ExternalPayrollException.class)
    ResponseEntity<ErrorResponse> handleExternalFailure(ExternalPayrollException exception) {
        HttpStatus status = exception.retryable()
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return response(status, exception.getMessage());
    }

    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class})
    ResponseEntity<ErrorResponse> handlePersistenceFailure() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                "Payroll records could not be persisted");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message));
    }

    record ErrorResponse(int status, String message) {
    }
}