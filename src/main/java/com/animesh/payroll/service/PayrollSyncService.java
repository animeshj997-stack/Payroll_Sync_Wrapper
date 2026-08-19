package com.animesh.payroll.service;

import com.animesh.payroll.domain.PayrollRecord;
import com.animesh.payroll.domain.PayrollRecordRepository;
import com.animesh.payroll.provider.ExternalPayrollRecord;
import com.animesh.payroll.provider.InvalidPayrollDataException;
import com.animesh.payroll.provider.PayrollProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PayrollSyncService {

    private static final Logger log = LoggerFactory.getLogger(PayrollSyncService.class);

    private final PayrollProviderFactory providerFactory;
    private final PayrollRecordRepository repository;
    private final TransactionTemplate transactionTemplate;

    public PayrollSyncService(PayrollProviderFactory providerFactory,
                              PayrollRecordRepository repository,
                              TransactionTemplate transactionTemplate) {
        this.providerFactory = providerFactory;
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    public int sync(String providerName, YearMonth payPeriod) {
        String syncId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        var provider = providerFactory.get(providerName);
        log.info("Payroll synchronization started syncId={} provider={} period={}",
            syncId, provider.providerName(), payPeriod);
        List<ExternalPayrollRecord> externalRecords = provider.fetchPayroll(payPeriod);
        log.info("Payroll records received syncId={} provider={} period={} records={}",
            syncId, provider.providerName(), payPeriod, externalRecords.size());
        Set<String> externalIds = new HashSet<>();
        List<PayrollRecord> records = externalRecords.stream().map(record -> {
            if (record == null || !externalIds.add(record.payrollRecordId())) {
            throw new InvalidPayrollDataException(
                "Payroll response contains a duplicate or null record");
            }
            return toEntity(record, provider.providerName(), payPeriod);
        }).toList();

        long persistenceStartedAt = System.nanoTime();
        transactionTemplate.executeWithoutResult(
                status -> repository.saveAll(records));
        log.info("Payroll synchronization completed syncId={} provider={} period={} records={} persistenceMs={} totalMs={}",
            syncId, provider.providerName(), payPeriod, records.size(),
            elapsedMillis(persistenceStartedAt), elapsedMillis(startedAt));
        return records.size();
    }

        private PayrollRecord toEntity(ExternalPayrollRecord source, String provider,
                       YearMonth payPeriod) {
        if (source.payrollRecordId() == null || source.payrollRecordId().isBlank()
            || source.employeeId() == null || source.employeeId().isBlank()) {
            throw new InvalidPayrollDataException(
                    "Payroll record ID and employee ID are required");
        }
        if (source.netPay() == null || source.netPay().signum() < 0) {
            throw new InvalidPayrollDataException(
                    "Net pay must be present and cannot be negative");
        }
        String providerScopedId = provider + ":" + source.payrollRecordId();
        return new PayrollRecord(providerScopedId, source.payrollRecordId(), source.employeeId(),
                source.grossPay(), source.taxAmount(), source.netPay(),
                source.paymentDate(), provider, payPeriod.toString());
        }

        private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
