package com.animesh.payroll.provider;

public class UnknownPayrollProviderException extends RuntimeException {

    public UnknownPayrollProviderException(String providerName) {
        super("No payroll provider configured for: " + providerName);
    }
}
