package com.example.demo.service;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstahyreScraperService {
    private final InstahyreConfig config;
    @Getter
    private WebDriver driver;
    private boolean isLoggedIn = false;

    public void initDriver() {
        if (driver == null) {
            try {
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();

                // Comment out headless for debugging
                options.addArguments("--headless");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36");

                // Disable automation flags
                options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
                options.setExperimentalOption("useAutomationExtension", false);

                driver = new ChromeDriver(options);

                // Execute CDP command to hide webdriver
                ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                        Map.of("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"));

                log.info("WebDriver initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize WebDriver", e);
                throw new RuntimeException("WebDriver initialization failed", e);
            }
        }
    }

    /**
     * Login using manual cookies
     */
    public void loginWithManualCookies(String sessionId, String csrfToken) {
        try {
            log.info("=== COOKIE-BASED LOGIN START ===");
            log.info("SessionID length: {}", sessionId != null ? sessionId.length() : 0);
            log.info("CSRF Token length: {}", csrfToken != null ? csrfToken.length() : 0);
            log.info("SessionID preview: {}...", sessionId != null && sessionId.length() > 20 ? sessionId.substring(0, 20) : "INVALID");

            if (sessionId == null || sessionId.isEmpty()) {
                throw new RuntimeException("SessionID is null or empty. Please check your application.yml configuration.");
            }

            if (csrfToken == null || csrfToken.isEmpty()) {
                throw new RuntimeException("CSRF Token is null or empty. Please check your application.yml configuration.");
            }

            // First, navigate to the main page (needed to set cookies on correct domain)
            log.info("Navigating to Instahyre homepage...");
            driver.get("https://www.instahyre.com");
            Thread.sleep(2000);

            log.info("Current URL: {}", driver.getCurrentUrl());

            // Add cookies
            log.info("Adding sessionid cookie...");
            driver.manage().addCookie(new Cookie("sessionid", sessionId));

            log.info("Adding csrftoken cookie...");
            driver.manage().addCookie(new Cookie("csrftoken", csrfToken));

            // Verify cookies were added
            Set<Cookie> cookies = driver.manage().getCookies();
            log.info("Total cookies after adding: {}", cookies.size());
            for (Cookie cookie : cookies) {
                log.info("  Cookie: {} = {}...", cookie.getName(),
                        cookie.getValue().length() > 20 ? cookie.getValue().substring(0, 20) : cookie.getValue());
            }

            // Refresh to apply cookies
            log.info("Refreshing page to apply cookies...");
            driver.navigate().refresh();
            Thread.sleep(3000);

            takeScreenshot("cookie-after-refresh");

            // Navigate to opportunities page to verify login
            log.info("Navigating to opportunities page to verify login...");
            driver.get("https://www.instahyre.com/candidate/opportunities");
            Thread.sleep(3000);

            String currentUrl = driver.getCurrentUrl();
            log.info("Current URL after navigation: {}", currentUrl);

            takeScreenshot("cookie-login-result");

            // Check if we're still on login page (login failed)
            if (Objects.requireNonNull(currentUrl).contains("login")) {
                log.error("❌ Still on login page - cookies are invalid or expired");
                log.error("Page title: {}", driver.getTitle());

                // Try to find error messages
                try {
                    List<WebElement> errors = driver.findElements(By.cssSelector(".error, .alert, [class*='error']"));
                    for (WebElement error : errors) {
                        if (error.isDisplayed()) {
                            log.error("Error message on page: {}", error.getText());
                        }
                    }
                } catch (Exception e) {
                    // No error elements
                }

                throw new RuntimeException("Cookie-based login failed - redirected to login page. Cookies may be expired. Please get fresh cookies from a new manual login session.");
            }

            // Check if we successfully reached opportunities page
            if (currentUrl.contains("opportunities") || currentUrl.contains("candidate")) {
                isLoggedIn = true;
                log.info("✅ Cookie-based login successful!");
                log.info("Page title: {}", driver.getTitle());
            } else {
                log.warn("⚠️  Unexpected page: {}", currentUrl);
                log.warn("Page title: {}", driver.getTitle());
                // Still set logged in flag if not on login page
                if (!currentUrl.contains("login")) {
                    isLoggedIn = true;
                    log.info("✅ Appears to be logged in (not on login page)");
                } else {
                    throw new RuntimeException("Cookie-based login failed - unexpected redirect");
                }
            }

            log.info("=== COOKIE-BASED LOGIN END ===");

        } catch (Exception e) {
            log.error("❌ Cookie-based login failed", e);
            takeScreenshot("cookie-login-error");
            throw new RuntimeException("Cookie-based login failed: " + e.getMessage(), e);
        }
    }

    private void takeScreenshot(String name) {
        try {
            if (driver instanceof TakesScreenshot) {
                File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                String fileName = name + "-" + System.currentTimeMillis() + ".png";
                String path = "screenshots/" + fileName;
                File destFile = new File(path);
                Files.copy(screenshot.toPath(), destFile.toPath());
                log.info("📸 Screenshot saved: {}", path);
            }
        } catch (Exception e) {
            log.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }

    public List<JobDTO> scrapeJobs() {
        if (!isLoggedIn) {
            throw new IllegalStateException("Must be logged in before scraping jobs");
        }

        try {
            log.info("=== SCRAPING JOBS ===");

            Map<String, String> cookieMap = driver.manage().getCookies().stream()
                    .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));

            String cookieHeader = cookieMap.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));

            String csrfToken = cookieMap.getOrDefault("csrftoken", "");

            RestClient restClient = RestClient.builder()
                    .baseUrl("https://www.instahyre.com")
                    .defaultHeader("Cookie", cookieHeader)
                    .defaultHeader("x-csrftoken", csrfToken)
                    .defaultHeader("accept", "application/json, text/plain, */*")
                    .defaultHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .defaultHeader("referer", "https://www.instahyre.com/candidate/opportunities/?matching=true")
                    .defaultHeader("origin", "https://www.instahyre.com")
                    .build();

            List<JobDTO> allJobs = new ArrayList<>();
            int offset = 0;
            int limit = 30;
            boolean hasMore = true;

            while (hasMore) {
                String url = String.format(
                        "/api/v1/candidate_opportunity?company_size=&industry_type=&interest_facet=0&job_type=&limit=%d&location=&offset=%d",
                        limit, offset
                );

                log.info("Fetching page at offset {}: {}", offset, url);

                String json = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(String.class);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);

                if (offset == 0) {
                    log.info("Response preview: {}", Objects.requireNonNull(json).substring(0, Math.min(300, json.length())));
                }

                JsonNode jobsNode = root.get("results");
                if (jobsNode == null) jobsNode = root.get("objects");

                if (jobsNode == null || !jobsNode.isArray() || jobsNode.isEmpty()) {
                    log.info("No more jobs found at offset {}", offset);
                    break;
                }

                int pageCount = 0; // ✅ reset per page
                for (JsonNode opp : jobsNode) {
                    try {
                        // ✅ correct fields for candidate_opportunity API
                        String opportunityId = opp.get("id").asText();
                        String company = opp.get("employer").get("company_name").asText();
                        String title = opp.get("job").get("candidate_title").asText();

                        List<String> skills = new ArrayList<>();
                        JsonNode keywords = opp.get("job").get("keywords");
                        if (keywords != null && keywords.isArray()) {
                            for (JsonNode kw : keywords) skills.add(kw.asText());
                        }

                        JobDTO jobDTO = JobDTO.builder()
                                .id(opportunityId)   // ✅ opportunity id e.g. "5850809550"
                                .jobId(null)         // ✅ null for opportunity jobs
                                .title(company + " - " + title)
                                .company(company)
                                .skills(skills)
                                .source("opportunity") // ✅ correct source
                                .build();

                        allJobs.add(jobDTO);
                        pageCount++; // ✅ increment count

                    } catch (Exception e) {
                        log.warn("Failed to parse opportunity: {}", e.getMessage());
                    }
                }

                log.info("Page offset={} fetched {} jobs (total so far: {})", offset, pageCount, allJobs.size());

                // ✅ pagination logic
                JsonNode nextNode = root.get("next");
                JsonNode totalNode = root.get("count");

                if (totalNode != null) {
                    int total = totalNode.asInt();
                    log.info("Total jobs available: {}", total);
                    offset += limit;
                    hasMore = offset < total;
                } else if (nextNode != null && !nextNode.isNull() && !nextNode.asText().isEmpty()) {
                    offset += limit;
                    // ✅ was missing before
                } else {
                    hasMore = pageCount == limit; // ✅ try next page if full page returned
                    offset += limit;
                }

                Thread.sleep(1000);
            }

            log.info("✅ Successfully scraped {} total jobs", allJobs.size());
            return allJobs;

        } catch (Exception e) {
            log.error("❌ Error fetching jobs via API", e);
            return new ArrayList<>();
        }
    }

    public List<JobDTO> scrapeJobSearch() {
        if (!config.getJobSearch().isEnabled()) {
            log.info("Job search scraping is disabled");
            return new ArrayList<>();
        }

        try {
            log.info("=== SCRAPING JOB SEARCH ===");

            Map<String, String> cookieMap = driver.manage().getCookies().stream()
                    .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));

            String cookieHeader = cookieMap.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));

            String csrfToken = cookieMap.getOrDefault("csrftoken", "");

            RestClient restClient = RestClient.builder()
                    .baseUrl("https://www.instahyre.com")
                    .defaultHeader("Cookie", cookieHeader)
                    .defaultHeader("x-csrftoken", csrfToken)
                    .defaultHeader("accept", "application/json, text/plain, */*")
                    .defaultHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .defaultHeader("referer", "https://www.instahyre.com/candidate/opportunities/?matching=true")
                    .defaultHeader("origin", "https://www.instahyre.com")
                    .build();

            // ✅ Exact URL from browser — guaranteed to work
            String baseParams = "company_size=0" +
                    "&jobLocations=Work+From+Home" +
                    "&jobLocations=North+India" +
                    "&jobLocations=Delhi+%2F+NCR" +
                    "&jobLocations=Dubai" +
                    "&jobLocations=United+Arab+Emirates+(UAE)" +
                    "&jobLocations=Qatar" +
                    "&jobLocations=Saudi+Arabia" +
                    "&jobLocations=Oman" +
                    "&jobLocations=Anywhere+in+Uttar+Pradesh" +
                    "&jobLocations=Bangalore" +
                    "&jobLocations=Hyderabad" +
                    "&job_functions=10" +
                    "&job_type=0" +
                    "&skills=Java" +
                    "&skills=Data+Structures" +
                    "&skills=Spring+Boot" +
                    "&skills=AWS" +
                    "&skills=J2EE" +
                    "&skills=MySQL" +
                    "&skills=Kafka" +
                    "&skills=Redis" +
                    "&skills=SQL" +
                    "&skills=Spring" +
                    "&skills=Docker" +
                    "&skills=Kubernetes" +
                    "&status=0" +
                    "&years=3";

            List<JobDTO> allJobs = new ArrayList<>();
            int offset = 0;
            int limit = 30;
            boolean hasMore = true;

            while (hasMore) {
                String url = "/api/v1/job_search?" + baseParams +
                        "&limit=" + limit + "&offset=" + offset;
                log.info("Fetching job_search at offset {}", offset);

                String json = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(String.class);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);

                if (offset == 0) {
                    log.info("job_search preview: {}",
                            Objects.requireNonNull(json).substring(0, Math.min(500, json.length())));
                }

                JsonNode jobsNode = root.get("objects");
                if (jobsNode == null) jobsNode = root.get("results");

                if (jobsNode == null || !jobsNode.isArray() || jobsNode.isEmpty()) {
                    log.info("No more job_search results at offset {}", offset);
                    break;
                }

                int pageCount = 0;
                for (JsonNode opp : jobsNode) {
                    try {
                        String jobId = opp.get("job").get("id").asText();
                        String company = opp.get("employer").get("company_name").asText();
                        String title = opp.get("job").get("candidate_title").asText();

                        List<String> skills = new ArrayList<>();
                        JsonNode keywords = opp.get("job").get("keywords");
                        if (keywords != null && keywords.isArray()) {
                            for (JsonNode kw : keywords) skills.add(kw.asText());
                        }

                        JobDTO jobDTO = JobDTO.builder()
                                .id(null)
                                .jobId(jobId)
                                .title(company + " - " + title)
                                .company(company)
                                .skills(skills)
                                .source("job_search")
                                .build();

                        allJobs.add(jobDTO);
                        pageCount++;

                    } catch (Exception e) {
                        log.warn("Failed to parse job_search result: {}", e.getMessage());
                    }
                }

                log.info("job_search offset={} fetched {} jobs (total: {})",
                        offset, pageCount, allJobs.size());

                JsonNode nextNode = root.get("next");
                JsonNode totalNode = root.get("count");

                if (totalNode != null) {
                    int total = totalNode.asInt();
                    log.info("Total job_search available: {}", total);
                    offset += limit;
                    hasMore = offset < total;
                } else if (nextNode != null && !nextNode.isNull() && !nextNode.asText().isEmpty()) {
                    offset += limit;
                } else {
                    hasMore = pageCount == limit;
                    offset += limit;
                }

                Thread.sleep(1000);
            }

            log.info("✅ job_search scraped {} total jobs", allJobs.size());
            return allJobs;

        } catch (Exception e) {
            log.error("❌ Error in scrapeJobSearch", e);
            return new ArrayList<>();
        }
    }
    public boolean applyToJob(JobDTO job) {
        try {
            log.info("Attempting to apply to job: {} (ID: {})", job.getTitle(), job.getId());

            Map<String, String> cookieMap = driver.manage().getCookies().stream()
                    .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));

            String cookieHeader = cookieMap.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));

            String csrfToken = cookieMap.getOrDefault("csrftoken", "");

            RestClient restClient = RestClient.builder()
                    .baseUrl("https://www.instahyre.com")
                    .defaultHeader("Cookie", cookieHeader)
                    .defaultHeader("x-csrftoken", csrfToken)
                    .defaultHeader("accept", "application/json, text/plain, */*")
                    .defaultHeader("content-type", "application/json")
                    .defaultHeader("origin", "https://www.instahyre.com")
                    .defaultHeader("referer", "https://www.instahyre.com/candidate/opportunities/?matching=true")
                    .defaultHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .build();

            // Build payload based on source
            Map<String, Object> body;
            if ("job_search".equals(job.getSource())) {
                // job_search payload: id=null, job_id=numeric
                body = new HashMap<>();
                body.put("id", null);
                body.put("is_interested", true);
                body.put("is_activity_page_job", false);
                body.put("job_id", Long.parseLong(job.getJobId()));
            } else {
                // opportunity payload: id=string, no job_id
                body = new HashMap<>();
                body.put("id", job.getId());
                body.put("is_interested", true);
                body.put("is_activity_page_job", false);
            }

            log.info("Applying with payload: {}", body);

            String response = restClient.post()
                    .uri("/api/v1/candidate_opportunity/apply")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("✅ Applied to: {} | Response: {}", job.getTitle(), response);
            return true;

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 400 && responseBody.contains("already applied")) {
                log.info("⏭️ Already applied to: {}", job.getTitle());
                return false;
            }
            log.error("❌ Failed to apply to: {} - {} - {}", job.getTitle(), e.getStatusCode(), responseBody);
            return false;
        } catch (Exception e) {
            log.error("❌ Failed to apply to: {} - {}", job.getTitle(), e.getMessage());
            return false;
        }
    }

    public void closeDriver() {
        if (driver != null) {
            try {
                driver.quit();
                driver = null;
                isLoggedIn = false;
                log.info("WebDriver closed successfully");
            } catch (Exception e) {
                log.error("Error closing WebDriver", e);
            }
        }
    }
}