package com.animesh.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "payroll.providers.papaya")
public record PayrollProviderProperties(
	String baseUrl,
	String apiToken,
	Duration connectTimeout,
	Duration readTimeout,
	int maxAttempts,
	Duration initialBackoff,
	Duration maxBackoff,
	boolean jitter) {
}
