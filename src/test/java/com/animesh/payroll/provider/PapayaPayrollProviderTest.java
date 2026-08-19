package com.animesh.payroll.provider;

import com.animesh.payroll.config.PayrollProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import java.time.Duration;
import java.time.YearMonth;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PapayaPayrollProviderTest {

    private static final YearMonth PERIOD = YearMonth.of(2026, 8);

    @Test
    void returnsRecordsFromSuccessfulApiCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(1), builder.build());

        server.expect(requestTo("/v1/payrolls?period=2026-08"))
                .andRespond(withSuccess("[{\"payrollRecordId\":\"p-1\",\"employeeId\":\"e-1\",\"netPay\":100.00}]",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        List<ExternalPayrollRecord> records = provider.fetchPayroll(PERIOD);

        assertEquals(1, records.size());
        assertEquals("p-1", records.getFirst().payrollRecordId());
        server.verify();
    }

    @Test
    void retries503ThenSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(3), builder.build());
        String url = "/v1/payrolls?period=2026-08";

        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(url)).andRespond(withSuccess("[]",
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertEquals(List.of(), provider.fetchPayroll(PERIOD));
        server.verify();
    }

    @Test
    void doesNotRetry400() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(3), builder.build());

        server.expect(requestTo("/v1/payrolls?period=2026-08"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        ExternalPayrollException exception = assertThrows(ExternalPayrollException.class,
                () -> provider.fetchPayroll(PERIOD));

        assertFalse(exception.retryable());
        server.verify();
    }

    @Test
    void retries429AndHonorsRetryAfterHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(2), builder.build());
        String url = "/v1/payrolls?period=2026-08";

        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "0"));
        server.expect(requestTo(url)).andRespond(withSuccess("[]",
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertEquals(List.of(), provider.fetchPayroll(PERIOD));
        server.verify();
    }

    @Test
    void doesNotRetry404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(3), builder.build());

        server.expect(requestTo("/v1/payrolls?period=2026-08"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ExternalPayrollException exception = assertThrows(ExternalPayrollException.class,
                () -> provider.fetchPayroll(PERIOD));

        assertFalse(exception.retryable());
        server.verify();
    }

    @Test
    void malformedResponseIsNotRetried() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(3), builder.build());

        server.expect(requestTo("/v1/payrolls?period=2026-08"))
                .andRespond(withSuccess("not-json",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        ExternalPayrollException exception = assertThrows(ExternalPayrollException.class,
                () -> provider.fetchPayroll(PERIOD));

        assertFalse(exception.retryable());
        server.verify();
    }

    @Test
    void respectsMaximumAttempts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties(3), builder.build());
        String url = "/v1/payrolls?period=2026-08";

        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        ExternalPayrollException exception = assertThrows(ExternalPayrollException.class,
                () -> provider.fetchPayroll(PERIOD));

                assertTrue(exception.retryable());
        server.verify();
    }

    @Test
    void retriesNetworkFailureAndEventuallyReportsExternalFailure() {
        PayrollProviderProperties properties = new PayrollProviderProperties(
                "http://127.0.0.1:1", "token", Duration.ofMillis(20),
                Duration.ofMillis(20), 3, Duration.ofMillis(1),
                Duration.ofMillis(2), false);
        PapayaPayrollProvider provider = new PapayaPayrollProvider(properties);

        ExternalPayrollException exception = assertThrows(ExternalPayrollException.class,
                () -> provider.fetchPayroll(PERIOD));

        assertEquals(true, exception.retryable());
    }

    @Test
    void convertsReadTimeoutIntoExternalFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payrolls", exchange -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            PayrollProviderProperties properties = new PayrollProviderProperties(
                    "http://localhost:" + server.getAddress().getPort(), "token",
                    Duration.ofSeconds(1), Duration.ofMillis(20), 1,
                    Duration.ZERO, Duration.ZERO, false);
            PapayaPayrollProvider provider = new PapayaPayrollProvider(properties);

            assertThrows(ExternalPayrollException.class,
                    () -> provider.fetchPayroll(PERIOD));
        } finally {
            server.stop(0);
        }
    }

    private PayrollProviderProperties properties(int maxAttempts) {
        return new PayrollProviderProperties(
                "http://payroll.test", "token", Duration.ofSeconds(1),
                Duration.ofSeconds(1), maxAttempts, Duration.ofMillis(1),
                Duration.ofMillis(2), false);
    }
}
