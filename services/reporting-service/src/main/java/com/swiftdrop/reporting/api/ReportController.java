package com.swiftdrop.reporting.api;

import com.swiftdrop.reporting.domain.DailyStats;
import com.swiftdrop.reporting.service.ReportService;
import com.swiftdrop.reporting.service.ReportService.Summary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-11 delivery reports for dispatchers and admins. Dates are ISO (yyyy-MM-dd) Sri Lankan days; both are optional
 * and default to the last 30 days.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('DISPATCHER', 'ADMIN')")
public class ReportController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/daily")
    public List<DailyStats> daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.daily(from, to);
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.summary(from, to);
    }

    /** The daily rows as a CSV download (dates and numbers only, so nothing a spreadsheet could execute). */
    @GetMapping(value = "/daily.csv", produces = "text/csv")
    public ResponseEntity<String> dailyCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<DailyStats> days = reports.daily(from, to);
        StringBuilder csv = new StringBuilder("date,created,delivered,failed,success_rate_percent\r\n");
        for (DailyStats day : days) {
            BigDecimal rate = ReportService.rate(day.delivered(), day.delivered() + day.failed());
            csv.append(day.day()).append(',').append(day.created()).append(',').append(day.delivered()).append(',')
                    .append(day.failed()).append(',').append(rate == null ? "" : rate.toPlainString()).append("\r\n");
        }
        String filename = "swiftdrop-deliveries-" + days.getFirst().day() + "-to-" + days.getLast().day() + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv.toString());
    }
}
