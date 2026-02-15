package com.example.demo.controller;

import com.example.demo.dto.JobDTO;
import com.example.demo.service.AutoApplierService;
import com.example.demo.service.InstahyreScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final AutoApplierService autoApplierService;
    private final InstahyreScraperService scraperService;

    @GetMapping
    public List<JobDTO> getJobs() {
        scraperService.initDriver();
        try {
            scraperService.login();
            return scraperService.scrapeJobs();
        } finally {
            // scraperService.closeDriver();
        }
    }

    @PostMapping("/apply")
    public List<JobDTO> apply() {
        return autoApplierService.runAutoApplier();
    }
}
