package com.example.demo.service;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AutoApplierService {

    private final InstahyreScraperService scraperService;
    private final InstahyreConfig config;

    @Scheduled(fixedRate = 3600000) // Run every hour
    public List<JobDTO> runAutoApplier() {
        log.info("Starting auto applier for Instahyre");
        scraperService.initDriver();
        try {
            scraperService.login();
            List<JobDTO> jobs = scraperService.scrapeJobs();
            List<JobDTO> applied = new ArrayList<>();
            for (JobDTO job : jobs) {
                if (matchesKeywords(job)) {
                    log.info("Applying to job: {}", job.getTitle());
                    scraperService.applyToJob(job);
                    applied.add(job);
                }
            }
            log.info("Auto applier completed");
            return applied;
        } finally {
            // scraperService.closeDriver();
        }
    }

    private boolean matchesKeywords(JobDTO job) {
        List<String> keywords = config.getKeywords();
        return job.getSkills().stream().anyMatch(skill ->
            keywords.stream().anyMatch(keyword -> skill.toLowerCase().contains(keyword.toLowerCase()))
        );
    }
}
