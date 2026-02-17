package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "instahyre")
@Data
public class InstahyreConfig {
    private String username;
    private String password;
    private List<String> keywords;

    // Cookie-based auth
    private String sessionid;
    private String csrftoken;
    private boolean useManualCookies = false;
}