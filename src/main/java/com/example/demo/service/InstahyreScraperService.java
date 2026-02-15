package com.example.demo.service;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstahyreScraperService {

    private final InstahyreConfig config;

    private WebDriver driver;

    public void initDriver() {
        if (driver == null) {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            // options.addArguments("--headless"); // Run in headless mode
            driver = new ChromeDriver(options);
        }
    }

    public void login() {
        if (Objects.requireNonNull(driver.getCurrentUrl()).contains("instahyre.com")) {
            return;
        }
        driver.get("https://www.instahyre.com/login");
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        // Click SSO sign-in (LinkedIn)
        WebElement ssoButton = wait.until(ExpectedConditions.elementToBeClickable(By.partialLinkText("Sign in with SSO")));
        ssoButton.click();

        // Wait for LinkedIn login page
        wait.until(ExpectedConditions.urlContains("linkedin.com"));
        // Enter LinkedIn credentials
        WebElement emailField = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("username")));
        WebElement passwordField = driver.findElement(By.id("password"));
        WebElement signInButton = driver.findElement(By.xpath("//button[@type='submit']"));

        Objects.requireNonNull(emailField).sendKeys(config.getUsername());
        passwordField.sendKeys(config.getPassword());
        signInButton.click();

        // Wait for redirect back to Instahyre
        wait.until(ExpectedConditions.urlContains("instahyre.com"));
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public List<JobDTO> scrapeJobs() {
        // Get cookies from Selenium
        Map<String, String> cookieMap = driver.manage().getCookies().stream()
            .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));
        String cookieHeader = cookieMap.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("; "));

        RestClient restClient = RestClient.builder()
            .baseUrl("https://www.instahyre.com")
            .defaultHeader("Cookie", cookieHeader)
            .defaultHeader("x-csrftoken", cookieMap.get("csrftoken"))
            .defaultHeader("accept", "application/json, text/plain, */*")
            .defaultHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
            .build();

        try {
            String json = restClient.get()
                .uri("/api/v1/candidate_opportunity?limit=30&offset=0")
                .retrieve()
                .body(String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.get("results");

            List<JobDTO> jobs = new ArrayList<>();
            for (JsonNode opp : results) {
                String company = opp.get("employer").get("company_name").asText();
                String titleText = opp.get("job").get("candidate_title").asText();
                String fullTitle = company + " - " + titleText;

                List<String> skills = new ArrayList<>();
                JsonNode keywords = opp.get("job").get("keywords");
                if (keywords != null && keywords.isArray()) {
                    for (JsonNode kw : keywords) {
                        skills.add(kw.asText());
                    }
                }

                JobDTO job = JobDTO.builder()
                    .title(fullTitle)
                    .skills(skills)
                    .build();
                jobs.add(job);
            }
            return jobs;
        } catch (Exception e) {
            log.error("Error fetching jobs via API", e);
            return new ArrayList<>();
        }
    }

    public void applyToJob(JobDTO job) {
        // Locate apply button in HTML by title
        String xpath = "//div[contains(@class, 'company-name') and contains(text(), '" + job.getTitle().replace("'", "\\'").replace("\"", "\\\"") + "')]//ancestor::div[contains(@class, 'employer-block')]//button[contains(text(), 'View')]";
        WebElement applyButton = driver.findElement(By.xpath(xpath));
        applyButton.click();
        // Perhaps confirm or wait
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void closeDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    public static class Job {
        private String title;
        private List<String> skills;
        private WebElement applyButton;

        public Job(String title, List<String> skills, WebElement applyButton) {
            this.title = title;
            this.skills = skills;
            this.applyButton = applyButton;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getSkills() {
            return skills;
        }

        public WebElement getApplyButton() {
            return applyButton;
        }
    }
}
