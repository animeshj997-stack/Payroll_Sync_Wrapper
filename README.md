# Payroll Sync Service

A Spring Boot service that fetches payroll records from an external provider,
validates them, and stores them in an H2 database during local development.

## How it works

```text
POST /api/payroll/sync
        |
        v
PayrollSyncController
        |
        v
PayrollSyncService
        |
        +--> PayrollProviderFactory --> PapayaPayrollProvider --> External API
        |
        v
PayrollRecordRepository --> Database
```

The service fetches the complete response before opening a database transaction.
It then validates, maps, and saves all records in one transaction.

## API

```bash
curl -X POST \
  "http://localhost:8080/api/payroll/sync?provider=papaya&period=2026-08"
```

A successful response looks like:

```json
{
  "provider": "papaya",
  "period": "2026-08",
  "recordsStored": 12
}
```

The Papaya provider calls:

```text
GET {base-url}/v1/payrolls?period=2026-08
Authorization: Bearer {api-token}
```

## Configuration

Configuration is in [application.yml](src/main/resources/application.yml).

```yaml
payroll:
  providers:
    papaya:
      base-url: ${PAPAYA_PAYROLL_BASE_URL:http://localhost:9090}
      api-token: ${PAPAYA_PAYROLL_API_TOKEN:local-development-token}
      connect-timeout: 3s
      read-timeout: 20s
      max-attempts: 3
      initial-backoff: 1s
      max-backoff: 4s
      jitter: true
```

For a real provider, set the environment variables:

```bash
export PAPAYA_PAYROLL_BASE_URL="https://provider.example.com"
export PAPAYA_PAYROLL_API_TOKEN="your-token"
```

## Retries

Temporary failures are retried up to three total attempts with exponential backoff:

- Network failures and timeouts
- HTTP `408`, `429`, `500`, `502`, `503`, and `504`

Permanent failures are not retried, including `400`, `401`, `403`, `404`, malformed
responses, validation errors, and database errors. `Retry-After` is respected for
numeric HTTP `429` responses.

## Data consistency

Each record is identified by its provider and external payroll ID. The database
also enforces uniqueness for `(provider, externalPayrollId)`.

This means:

- Repeating a sync does not create duplicate records.
- Changed values for an existing external ID update the same record.
- Duplicate IDs in one API response are rejected before persistence.
- A database failure does not intentionally commit a partial batch.

## Project structure

```text
src/main/java/com/animesh/payroll/
  api/       REST endpoint and error handling
  config/    Provider configuration
  domain/    JPA entity and repository
  provider/  Provider implementations and API models
  service/   Validation, mapping, and synchronization
```

## Run locally

Requirements: Java 21 and Maven 3.9+.

```bash
mvn spring-boot:run
mvn clean test
```

The default external API URL is `http://localhost:9090`.
