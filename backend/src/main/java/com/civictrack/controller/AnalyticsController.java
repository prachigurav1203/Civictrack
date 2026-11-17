package com.civictrack.controller;

import com.civictrack.model.Category;
import com.civictrack.service.AnalyticsService;
import com.civictrack.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final ReportExportService export;

    @GetMapping("/summary")
    public Map<String, Object> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Category category) {
        return analytics.summary(from, to, departmentId, category);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Category category) throws IOException {
        return file(export.excel(from, to, departmentId, category), "civictrack-report.xlsx",
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Category category) {
        return file(export.pdf(from, to, departmentId, category), "civictrack-report.pdf", MediaType.APPLICATION_PDF);
    }

    private ResponseEntity<byte[]> file(byte[] data, String name, MediaType type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(type).body(data);
    }
}
