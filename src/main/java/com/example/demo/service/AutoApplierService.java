package com.example.demo.service;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoApplierService {

    private final InstahyreScraperService scraperService;
    private final InstahyreConfig config;

    private final Set<String> appliedJobIds = new HashSet<>();

    @Scheduled(fixedRate = 3600000) // Run every hour
    public void scheduledAutoApplier() {
        log.info("Scheduled auto applier triggered");
        runAutoApplier();
    }

    public List<JobDTO> runAutoApplier() {
        log.info("Starting auto applier for Instahyre");
        List<JobDTO> appliedJobs = new ArrayList<>();

        try {
            scraperService.initDriver();

            // Check if we should use manual cookies or automatic login
            if (config.isUseManualCookies()) {
                if (config.getSessionid() == null || config.getCsrftoken() == null) {
                    log.error("Manual cookies enabled but sessionid or csrftoken is missing!");
                    log.error("Please add 'sessionid' and 'csrftoken' to your application.yml");
                    log.error("See MANUAL_COOKIE_LOGIN_GUIDE.md for instructions");
                    return appliedJobs;
                }

                log.info("Using manual cookie-based authentication");
                scraperService.loginWithManualCookies(
                        config.getSessionid(),
                        config.getCsrftoken()
                );
            } else {
                log.info("Using automatic login");
                scraperService.login();
            }

            List<JobDTO> jobs = scraperService.scrapeJobs();
            log.info("Found {} total jobs", jobs.size());

            int matchedCount = 0;
            int appliedCount = 0;
            int skippedCount = 0;

            for (JobDTO job : jobs) {
                try {
                    if (appliedJobIds.contains(job.getId())) {
                        log.info("Skipping already applied job: {}", job.getTitle());
                        skippedCount++;
                        continue;
                    }

                    if (matchesKeywords(job)) {
                        matchedCount++;
                        log.info("Job matches keywords: {} (Skills: {})",
                                job.getTitle(), String.join(", ", job.getSkills()));

                        boolean success = scraperService.applyToJob(job);

                        if (success) {
                            appliedCount++;
                            job.setApplied(true);
                            appliedJobs.add(job);
                            appliedJobIds.add(job.getId());
                            log.info("Successfully applied to: {}", job.getTitle());
                        } else {
                            log.warn("Failed to apply to: {}", job.getTitle());
                        }

                        Thread.sleep(3000);
                    }
                } catch (Exception e) {
                    log.error("Error processing job: {}", job.getTitle(), e);
                }
            }

            log.info("Auto applier completed - Total: {}, Matched: {}, Applied: {}, Skipped: {}",
                    jobs.size(), matchedCount, appliedCount, skippedCount);

        } catch (Exception e) {
            log.error("Auto applier failed", e);
        } finally {
            // Optionally keep driver open for next run
            // scraperService.closeDriver();
        }

        return appliedJobs;
    }

    private boolean matchesKeywords(JobDTO job) {
        if (job.getSkills() == null || job.getSkills().isEmpty()) {
            log.debug("Job {} has no skills listed", job.getTitle());
            return false;
        }

        List<String> keywords = config.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            log.warn("No keywords configured in application properties");
            return false;
        }

        boolean matches = job.getSkills().stream().anyMatch(skill ->
                keywords.stream().anyMatch(keyword ->
                        skill.toLowerCase().contains(keyword.toLowerCase())
                )
        );

        if (matches) {
            List<String> matchedKeywords = job.getSkills().stream()
                    .filter(skill -> keywords.stream().anyMatch(keyword ->
                            skill.toLowerCase().contains(keyword.toLowerCase())
                    ))
                    .toList();
            log.debug("Matched keywords for {}: {}", job.getTitle(), matchedKeywords);
        }

        return matches;
    }

    public void clearAppliedJobs() {
        appliedJobIds.clear();
        log.info("Cleared applied jobs history");
    }

    public int getAppliedJobsCount() {
        return appliedJobIds.size();
    }
}