package com.animesh.payroll.provider;

import com.animesh.payroll.config.PayrollProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PapayaPayrollProvider implements PayrollProvider {

    private static final Logger log = LoggerFactory.getLogger(PapayaPayrollProvider.class);

    private final RestClient restClient;
    private final PayrollProviderProperties properties;

    public PapayaPayrollProvider(PayrollProviderProperties properties) {
        this(properties, RestClient.builder());
    }

    PapayaPayrollProvider(PayrollProviderProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.properties = properties;
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                .build();
    }

    PapayaPayrollProvider(PayrollProviderProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public String providerName() {
        return "papaya";
    }

    @Override
    public List<ExternalPayrollRecord> fetchPayroll(YearMonth payPeriod) {
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                List<ExternalPayrollRecord> response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v1/payrolls")
                                .queryParam("period", payPeriod)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {
                        });
                log.info("Payroll API request succeeded provider={} period={} attempt={} records={}",
                        providerName(), payPeriod, attempt, response == null ? 0 : response.size());
                return response == null ? List.of() : response;
            } catch (ExternalPayrollException exception) {
                if (!exception.retryable() || attempt == properties.maxAttempts()) {
                    log.warn("Payroll API request failed provider={} period={} attempt={} retryable={} status={}",
                            providerName(), payPeriod, attempt, exception.retryable(),
                            exception.statusCode() == null ? "none" : exception.statusCode().value());
                    throw exception;
                }
                retry(payPeriod, attempt, exception.retryAfter(), exception);
            } catch (ResourceAccessException exception) {
                if (attempt == properties.maxAttempts()) {
                    throw externalFailure(payPeriod, exception, true);
                }
                retry(payPeriod, attempt, null, exception);
            } catch (HttpStatusCodeException exception) {
                var status = exception.getStatusCode();
                ExternalPayrollException externalException = new ExternalPayrollException(
                        "Payroll API returned HTTP " + status.value(), exception,
                        isRetryable(status), status, retryAfter(exception.getResponseHeaders()));
                if (!externalException.retryable() || attempt == properties.maxAttempts()) {
                    throw externalException;
                }
                retry(payPeriod, attempt, externalException.retryAfter(), externalException);
            } catch (RestClientException exception) {
                throw externalFailure(payPeriod, exception, false);
            }
        }
        throw new IllegalStateException("Payroll API retry loop ended unexpectedly");
    }

    private void retry(YearMonth payPeriod, int attempt, Duration retryAfter,
                       RuntimeException exception) {
        Duration delay = retryAfter == null ? backoff(attempt) : cap(retryAfter);
        log.warn("Retrying payroll API request provider={} period={} attempt={} nextDelayMs={} cause={}",
                providerName(), payPeriod, attempt + 1, delay.toMillis(),
                exception.getClass().getSimpleName());
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw externalFailure(payPeriod, interruptedException, true);
        }
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 30);
        Duration configured = properties.initialBackoff().multipliedBy(multiplier);
        Duration capped = cap(configured);
        if (!properties.jitter() || capped.isZero()) {
            return capped;
        }
        long jitterMillis = ThreadLocalRandom.current().nextLong(capped.toMillis() + 1);
        return Duration.ofMillis(jitterMillis);
    }

    private Duration cap(Duration value) {
        return value.compareTo(properties.maxBackoff()) > 0
                ? properties.maxBackoff() : value;
    }

    private ExternalPayrollException externalFailure(YearMonth payPeriod,
                                                     Throwable cause,
                                                     boolean retryable) {
        return new ExternalPayrollException(
                "Could not fetch payroll data for " + payPeriod, cause,
                retryable, null, null);
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.value() == 408 || status.value() == 429
                || status.value() == 500 || status.value() == 502
                || status.value() == 503 || status.value() == 504;
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst("Retry-After");
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
