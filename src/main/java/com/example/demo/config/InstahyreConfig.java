package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "instahyre")
@Data
public class InstahyreConfig {
    private String username;
    private String password;
    private List<String> keywords;
    private boolean useManualCookies;
    private String sessionid;
    private String csrftoken;
    private JobSearchConfig jobSearch = new JobSearchConfig();

    @Data
    public static class JobSearchConfig {
        private boolean enabled = true;
        private List<String> skills = new ArrayList<>();
        private List<String> locations = new ArrayList<>();
        private int yearsOfExperience = 3;
    }
}