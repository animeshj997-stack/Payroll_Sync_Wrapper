package com.animesh.payroll.provider;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollProviderFactoryTest {

    @Test
    void returnsProviderIgnoringCase() {
        PayrollProvider provider = new StubProvider("papaya");
        PayrollProviderFactory factory = new PayrollProviderFactory(List.of(provider));

        assertSame(provider, factory.get("PAPAYA"));
    }

    @Test
    void rejectsUnknownProvider() {
        PayrollProviderFactory factory = new PayrollProviderFactory(List.of());

        assertThrows(UnknownPayrollProviderException.class,
                () -> factory.get("unknown"));
    }

    private record StubProvider(String providerName) implements PayrollProvider {
        @Override
        public List<ExternalPayrollRecord> fetchPayroll(YearMonth payPeriod) {
            return List.of();
        }
    }
}
