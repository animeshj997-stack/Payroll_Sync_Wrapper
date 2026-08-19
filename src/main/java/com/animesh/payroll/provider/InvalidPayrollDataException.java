package com.animesh.payroll.provider;

public class InvalidPayrollDataException extends RuntimeException {

    public InvalidPayrollDataException(String message) {
        super(message);
    }
}
