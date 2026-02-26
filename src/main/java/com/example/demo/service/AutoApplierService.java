package com.example.demo.service;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoApplierService {

    private final InstahyreScraperService scraperService;
    private final InstahyreConfig config;

    // Track applied jobs by a unique key (opportunityId or jobId)
    private final Set<String> appliedJobIds = Collections.synchronizedSet(new HashSet<>());

    @Scheduled(fixedRate = 3600000)
    public void scheduledAutoApplier() {
        log.info("Scheduled auto applier triggered");
        runAutoApplier();
    }

    public List<JobDTO> runAutoApplier() {
        log.info("Starting auto applier for Instahyre");
        List<JobDTO> appliedJobs = new ArrayList<>();

        try {
            scraperService.initDriver();

            if (!config.isUseManualCookies()) {
                log.error("❌ Please enable cookie-based authentication in application.yml");
                return appliedJobs;
            }

            if (config.getSessionid() == null || config.getCsrftoken() == null) {
                log.error("❌ sessionid or csrftoken missing in application.yml");
                return appliedJobs;
            }

            scraperService.loginWithManualCookies(
                    config.getSessionid(),
                    config.getCsrftoken()
            );

            // Scrape both sources
            List<JobDTO> opportunityJobs = scraperService.scrapeJobs();
            log.info("Scraped {} opportunity jobs", opportunityJobs.size());

            List<JobDTO> jobSearchJobs = scraperService.scrapeJobSearch();
            log.info("Scraped {} job_search jobs", jobSearchJobs.size());

            // Merge, deduplicating by unique key
            Map<String, JobDTO> allJobsMap = new LinkedHashMap<>();
            for (JobDTO j : opportunityJobs) {
                if (j.getId() != null) allJobsMap.put("opp_" + j.getId(), j);
            }
            for (JobDTO j : jobSearchJobs) {
                if (j.getJobId() != null) allJobsMap.putIfAbsent("job_" + j.getJobId(), j);
            }

            // ✅ Apply to ALL jobs — no keyword filtering
            List<JobDTO> allJobs = new ArrayList<>(allJobsMap.values());
            log.info("Total unique jobs to apply: {} ({} opportunity + {} job_search)",
                    allJobs.size(), opportunityJobs.size(), jobSearchJobs.size());

            int appliedCount = 0;
            int skippedCount = 0;

            for (JobDTO job : allJobs) {
                try {
                    // Unique key per source
                    String uniqueKey = "opportunity".equals(job.getSource())
                            ? "opp_" + job.getId()
                            : "job_" + job.getJobId();

                    if (appliedJobIds.contains(uniqueKey)) {
                        log.info("⏭️ Skipping already applied: {}", job.getTitle());
                        skippedCount++;
                        continue;
                    }

                    boolean success = scraperService.applyToJob(job);

                    if (success) {
                        appliedCount++;
                        job.setApplied(true);
                        appliedJobs.add(job);
                        appliedJobIds.add(uniqueKey);
                        log.info("✅ Applied to: {} [{}] (Total: {})",
                                job.getTitle(), job.getSource(), appliedCount);
                    } else {
                        log.warn("❌ Failed to apply to: {}", job.getTitle());
                    }

                    Thread.sleep(3000);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while processing: {}", job.getTitle());
                    break;
                } catch (Exception e) {
                    log.error("Error processing job: {}", job.getTitle(), e);
                }
            }

            log.info("✅ Done - Total: {}, Applied: {}, Skipped: {}",
                    allJobs.size(), appliedCount, skippedCount);

        } catch (Exception e) {
            log.error("Auto applier failed", e);
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
            log.warn("No keywords configured in application.yml");
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