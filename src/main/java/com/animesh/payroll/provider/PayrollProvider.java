package com.animesh.payroll.provider;

import java.time.YearMonth;
import java.util.List;

public interface PayrollProvider {

    String providerName();

    List<ExternalPayrollRecord> fetchPayroll(YearMonth payPeriod);
}
