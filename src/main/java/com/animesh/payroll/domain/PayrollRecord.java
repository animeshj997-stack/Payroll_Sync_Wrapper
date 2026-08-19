package com.animesh.payroll.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_records", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
    name = "uk_payroll_provider_external_id", columnNames = {"provider", "external_payroll_id"}))
public class PayrollRecord {

    @Id
    private String id;
    private String externalPayrollId;
    private String employeeId;
    private BigDecimal grossPay;
    private BigDecimal taxAmount;
    private BigDecimal netPay;
    private LocalDate paymentDate;
    private String provider;
    private String payPeriod;

    protected PayrollRecord() {
    }

    public PayrollRecord(String id, String externalPayrollId, String employeeId,
                         BigDecimal grossPay, BigDecimal taxAmount,
                         BigDecimal netPay, LocalDate paymentDate,
                         String provider, String payPeriod) {
        this.id = id;
        this.externalPayrollId = externalPayrollId;
        this.employeeId = employeeId;
        this.grossPay = grossPay;
        this.taxAmount = taxAmount;
        this.netPay = netPay;
        this.paymentDate = paymentDate;
        this.provider = provider;
        this.payPeriod = payPeriod;
    }

    public String getId() { return id; }
    public String getExternalPayrollId() { return externalPayrollId; }
    public String getEmployeeId() { return employeeId; }
    public BigDecimal getGrossPay() { return grossPay; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getNetPay() { return netPay; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public String getProvider() { return provider; }
    public String getPayPeriod() { return payPeriod; }
}
