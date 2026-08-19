package com.animesh.payroll.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PayrollProviderFactory {

    private final Map<String, PayrollProvider> providers;

    public PayrollProviderFactory(List<PayrollProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        provider -> provider.providerName().toLowerCase(),
                        Function.identity()));
    }

    public PayrollProvider get(String providerName) {
        PayrollProvider provider = providers.get(providerName.toLowerCase());
        if (provider == null) {
            throw new UnknownPayrollProviderException(providerName);
        }
        return provider;
    }
}
