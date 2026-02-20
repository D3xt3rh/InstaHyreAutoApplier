package com.example.demo.controller;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import com.example.demo.service.AutoApplierService;
import com.example.demo.service.InstahyreScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final AutoApplierService autoApplierService;
    private final InstahyreScraperService scraperService;
    private final InstahyreConfig config;

    /**
     * Get all available jobs from Instahyre
     */
    @GetMapping
    public ResponseEntity<?> getJobs() {
        try {
            scraperService.initDriver();

            // Use cookie-based login
            if (config.isUseManualCookies()) {
                if (config.getSessionid() == null || config.getCsrftoken() == null) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", "Cookie-based auth enabled but cookies are missing. Please add sessionid and csrftoken to application.yml");
                    return ResponseEntity.badRequest().body(error);
                }

                scraperService.loginWithManualCookies(
                        config.getSessionid(),
                        config.getCsrftoken()
                );
            } else {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Automatic login is not supported. Please enable cookie-based authentication in application.yml (set use-manual-cookies: true)");
                return ResponseEntity.badRequest().body(error);
            }

            List<JobDTO> jobs = scraperService.scrapeJobs();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", jobs.size());
            response.put("jobs", jobs);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching jobs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Manually trigger auto-applier
     */
    @PostMapping("/apply")
    public ResponseEntity<?> applyToJobs() {
        try {
            List<JobDTO> appliedJobs = autoApplierService.runAutoApplier();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("appliedCount", appliedJobs.size());
            response.put("appliedJobs", appliedJobs);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error applying to jobs", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get status of auto-applier
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("appliedJobsCount", autoApplierService.getAppliedJobsCount());
        return ResponseEntity.ok(status);
    }

    /**
     * Clear applied jobs history
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetAppliedJobs() {
        autoApplierService.clearAppliedJobs();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Applied jobs history cleared");
        return ResponseEntity.ok(response);
    }

    /**
     * Close the browser driver
     */
    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup() {
        scraperService.closeDriver();
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Browser driver closed");
        return ResponseEntity.ok(response);
    }
}