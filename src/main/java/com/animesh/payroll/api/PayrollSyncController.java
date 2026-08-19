package com.animesh.payroll.api;

import com.animesh.payroll.service.PayrollSyncService;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/payroll")
@Validated
public class PayrollSyncController {

    private final PayrollSyncService syncService;

    public PayrollSyncController(PayrollSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> sync(
            @RequestParam @Pattern(regexp = "[a-zA-Z]+") String provider,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth period) {
        int count = syncService.sync(provider, period);
        return ResponseEntity.ok(new SyncResponse(provider, period, count));
    }

    public record SyncResponse(String provider, YearMonth period, int recordsStored) {
    }
}
