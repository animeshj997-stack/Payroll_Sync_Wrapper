package com.animesh.payroll.service;

import com.animesh.payroll.domain.PayrollRecord;
import com.animesh.payroll.domain.PayrollRecordRepository;
import com.animesh.payroll.provider.ExternalPayrollRecord;
import com.animesh.payroll.provider.InvalidPayrollDataException;
import com.animesh.payroll.provider.PayrollProvider;
import com.animesh.payroll.provider.PayrollProviderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollSyncServiceTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 8);

    @Test
    void emptyApiResponseStoresNothing() {
        PayrollProvider provider = providerReturning(List.of());
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);
        PayrollSyncService service = service(provider, repository);

        assertEquals(0, service.sync("papaya", PERIOD));
        verify(repository).saveAll(List.of());
    }

    @Test
    void persistsMappedRecordsAndReturnsRecordCount() {
        PayrollProvider provider = providerReturning(List.of(record("p-1", "100.00")));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);
        PayrollSyncService service = service(provider, repository);

        assertEquals(1, service.sync("PAPAYA", PERIOD));

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        PayrollRecord persisted = (PayrollRecord) captor.getValue().getFirst();
        assertEquals("papaya:p-1", persisted.getId());
        assertEquals("p-1", persisted.getExternalPayrollId());
        assertEquals("e-1", persisted.getEmployeeId());
        assertEquals("100.00", persisted.getNetPay().toPlainString());
    }

    @Test
    void rejectsMissingPayrollRecordId() {
        assertInvalidBeforePersistence(new ExternalPayrollRecord(
                null, "e-1", BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, LocalDate.of(2026, 8, 31)));
    }

    @Test
    void rejectsMissingEmployeeId() {
        assertInvalidBeforePersistence(new ExternalPayrollRecord(
                "p-1", null, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, LocalDate.of(2026, 8, 31)));
    }

    @Test
    void acceptsZeroNetPay() {
        PayrollProvider provider = providerReturning(List.of(
                new ExternalPayrollRecord("p-1", "e-1", BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        LocalDate.of(2026, 8, 31))));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);

        assertEquals(1, service(provider, repository).sync("papaya", PERIOD));
        verify(repository).saveAll(anyList());
    }

    @Test
    void providerFailurePreventsPersistence() {
        PayrollProvider provider = mock(PayrollProvider.class);
        when(provider.providerName()).thenReturn("papaya");
        when(provider.fetchPayroll(PERIOD)).thenThrow(
                new com.animesh.payroll.provider.ExternalPayrollException(
                        "provider unavailable", new RuntimeException(), true, null, null));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);

        assertThrows(com.animesh.payroll.provider.ExternalPayrollException.class,
                () -> service(provider, repository).sync("papaya", PERIOD));
        verify(repository, times(0)).saveAll(anyList());
    }

    @Test
    void repeatedRecordUsesStableIdentityAndUpdatedValues() {
        ExternalPayrollRecord original = record("p-1", "100.00");
        ExternalPayrollRecord updated = record("p-1", "125.00");
        PayrollProvider provider = mock(PayrollProvider.class);
        when(provider.providerName()).thenReturn("papaya");
        when(provider.fetchPayroll(PERIOD)).thenReturn(List.of(original), List.of(updated));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);
        PayrollSyncService service = service(provider, repository);

        service.sync("papaya", PERIOD);
        service.sync("papaya", PERIOD);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).saveAll(captor.capture());
        List<PayrollRecord> first = captor.getAllValues().get(0);
        List<PayrollRecord> second = captor.getAllValues().get(1);
        assertEquals(first.get(0).getId(), second.get(0).getId());
        assertEquals("p-1", second.get(0).getExternalPayrollId());
        assertEquals("125.00", second.get(0).getNetPay().toPlainString());
        assertEquals("2026-08", second.get(0).getPayPeriod());
    }

    @Test
    void rejectsDuplicateExternalIdsBeforePersistence() {
        ExternalPayrollRecord record = record("p-1", "100.00");
        PayrollProvider provider = providerReturning(List.of(record, record));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);
        PayrollSyncService service = service(provider, repository);

        assertThrows(InvalidPayrollDataException.class, () -> service.sync("papaya", PERIOD));
        verify(repository, times(0)).saveAll(anyList());
    }

    @Test
    void rejectsInvalidPayrollDataBeforePersistence() {
        PayrollProvider provider = providerReturning(List.of(
                new ExternalPayrollRecord("p-1", "e-1", null, null,
                        BigDecimal.valueOf(-1), LocalDate.of(2026, 8, 31))));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);
        PayrollSyncService service = service(provider, repository);

        assertThrows(InvalidPayrollDataException.class, () -> service.sync("papaya", PERIOD));
        verify(repository, times(0)).saveAll(anyList());
    }

    @Test
    void propagatesPersistenceFailure() {
        PayrollProvider provider = providerReturning(List.of(record("p-1", "100.00")));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(repository).saveAll(anyList());
        PayrollSyncService service = service(provider, repository);

        assertThrows(DataAccessResourceFailureException.class,
                () -> service.sync("papaya", PERIOD));
    }

    private PayrollSyncService service(PayrollProvider provider,
                                       PayrollRecordRepository repository) {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<?> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        return new PayrollSyncService(new PayrollProviderFactory(List.of(provider)),
                repository, transactionTemplate);
    }

    private PayrollProvider providerReturning(List<ExternalPayrollRecord> records) {
        PayrollProvider provider = mock(PayrollProvider.class);
        when(provider.providerName()).thenReturn("papaya");
        when(provider.fetchPayroll(PERIOD)).thenReturn(records);
        return provider;
    }

    private ExternalPayrollRecord record(String id, String netPay) {
        return new ExternalPayrollRecord(id, "e-1", BigDecimal.valueOf(150),
                BigDecimal.valueOf(50), new BigDecimal(netPay),
                LocalDate.of(2026, 8, 31));
    }

    private void assertInvalidBeforePersistence(ExternalPayrollRecord invalidRecord) {
        PayrollProvider provider = providerReturning(List.of(invalidRecord));
        PayrollRecordRepository repository = mock(PayrollRecordRepository.class);

        assertThrows(InvalidPayrollDataException.class,
                () -> service(provider, repository).sync("papaya", PERIOD));
        verify(repository, times(0)).saveAll(anyList());
    }
}
