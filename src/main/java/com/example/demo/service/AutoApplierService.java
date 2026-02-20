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

            // Check if manual cookies are enabled
            if (!config.isUseManualCookies()) {
                log.error("❌ Automatic login is not supported!");
                log.error("Please enable cookie-based authentication:");
                log.error("1. Set 'use-manual-cookies: true' in application.yml");
                log.error("2. Add 'sessionid' and 'csrftoken' values");
                log.error("3. See GET_FRESH_COOKIES_GUIDE.md for instructions");
                return appliedJobs;
            }

            // Check if cookies are provided
            if (config.getSessionid() == null || config.getCsrftoken() == null) {
                log.error("❌ Manual cookies enabled but sessionid or csrftoken is missing!");
                log.error("Please add 'sessionid' and 'csrftoken' to your application.yml");
                log.error("See GET_FRESH_COOKIES_GUIDE.md for instructions");
                return appliedJobs;
            }

            log.info("Using manual cookie-based authentication");
            scraperService.loginWithManualCookies(
                    config.getSessionid(),
                    config.getCsrftoken()
            );

            List<JobDTO> allJobs = scraperService.scrapeJobs();
            log.info("Found {} total jobs", allJobs.size());

            List<JobDTO> matched = allJobs.stream().filter(this::matchesKeywords).toList();
            log.info("Found {} matched jobs", matched.size());

            int appliedCount = 0;
            int skippedCount = 0;

            // Navigate to opportunities page once (not needed now, but keep for login check)
            // scraperService.getDriver().get("https://www.instahyre.com/candidate/opportunities");
            // Thread.sleep(2000); // Wait for page load

            for (JobDTO job : matched) {
                try {
                    if (appliedJobIds.contains(job.getId())) {
                        log.info("Skipping already applied job: {}", job.getTitle());
                        skippedCount++;
                        continue;
                    }

                    boolean success = scraperService.applyToJob(job);

                    if (success) {
                        appliedCount++;
                        job.setApplied(true);
                        appliedJobs.add(job);
                        appliedJobIds.add(job.getId());
                        log.info("Successfully applied to: {} (Total applied: {})", job.getTitle(), appliedCount);
                    } else {
                        log.warn("Failed to apply to: {}", job.getTitle());
                    }

                    Thread.sleep(3000); // Delay between applications
                } catch (Exception e) {
                    log.error("Error processing job: {}", job.getTitle(), e);
                }
            }

            log.info("Auto applier completed - Total jobs: {}, Matched: {}, Applied: {}, Skipped: {}",
                    allJobs.size(), matched.size(), appliedCount, skippedCount);

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