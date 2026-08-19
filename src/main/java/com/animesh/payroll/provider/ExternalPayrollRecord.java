package com.animesh.payroll.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExternalPayrollRecord(
        String payrollRecordId,
        String employeeId,
        BigDecimal grossPay,
        BigDecimal taxAmount,
        BigDecimal netPay,
        LocalDate paymentDate) {
}
