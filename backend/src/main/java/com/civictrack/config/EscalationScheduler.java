package com.civictrack.config;

import com.civictrack.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SLA monitoring: periodically escalates complaints that passed their deadline. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscalationScheduler {
    private final ComplaintService complaints;

    @Scheduled(initialDelayString = "${app.sla.initial-delay-ms:30000}", fixedDelayString = "${app.sla.check-interval-ms:600000}")
    public void run() {
        try {
            int n = complaints.escalateOverdue();
            if (n > 0) log.info("SLA check: {} complaint(s) escalated", n);
        } catch (Exception e) {
            log.error("SLA escalation check failed", e);
        }
    }
}
