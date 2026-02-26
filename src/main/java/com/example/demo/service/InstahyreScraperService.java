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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.net.URI;
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
                options.addArguments("--headless");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-gpu");
                options.addArguments("--window-size=1920,1080");
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36");
                options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
                options.setExperimentalOption("useAutomationExtension", false);

                driver = new ChromeDriver(options);
                ((ChromeDriver) driver).executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                        Map.of("source", "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})"));

                log.info("WebDriver initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize WebDriver", e);
                throw new RuntimeException("WebDriver initialization failed", e);
            }
        }
    }

    public void loginWithManualCookies(String sessionId, String csrfToken) {
        try {
            log.info("=== COOKIE-BASED LOGIN START ===");
            log.info("SessionID length: {}", sessionId != null ? sessionId.length() : 0);
            log.info("CSRF Token length: {}", csrfToken != null ? csrfToken.length() : 0);
            log.info("SessionID preview: {}...", sessionId != null && sessionId.length() > 20 ? sessionId.substring(0, 20) : "INVALID");

            if (sessionId == null || sessionId.isEmpty()) {
                throw new RuntimeException("SessionID is null or empty.");
            }
            if (csrfToken == null || csrfToken.isEmpty()) {
                throw new RuntimeException("CSRF Token is null or empty.");
            }

            log.info("Navigating to Instahyre homepage...");
            driver.get("https://www.instahyre.com");
            Thread.sleep(2000);
            log.info("Current URL: {}", driver.getCurrentUrl());

            log.info("Adding sessionid cookie...");
            driver.manage().addCookie(new Cookie("sessionid", sessionId));
            log.info("Adding csrftoken cookie...");
            driver.manage().addCookie(new Cookie("csrftoken", csrfToken));

            Set<Cookie> cookies = driver.manage().getCookies();
            log.info("Total cookies after adding: {}", cookies.size());
            for (Cookie cookie : cookies) {
                log.info("  Cookie: {} = {}...", cookie.getName(),
                        cookie.getValue().length() > 20 ? cookie.getValue().substring(0, 20) : cookie.getValue());
            }

            log.info("Refreshing page to apply cookies...");
            driver.navigate().refresh();
            Thread.sleep(3000);
            takeScreenshot("cookie-after-refresh");

            log.info("Navigating to opportunities page to verify login...");
            driver.get("https://www.instahyre.com/candidate/opportunities");
            Thread.sleep(3000);

            String currentUrl = driver.getCurrentUrl();
            log.info("Current URL after navigation: {}", currentUrl);
            takeScreenshot("cookie-login-result");

            if (Objects.requireNonNull(currentUrl).contains("login")) {
                log.error("❌ Still on login page - cookies are invalid or expired");
                throw new RuntimeException("Cookie-based login failed - redirected to login page.");
            }

            if (currentUrl.contains("opportunities") || currentUrl.contains("candidate")) {
                isLoggedIn = true;
                log.info("✅ Cookie-based login successful!");
                log.info("Page title: {}", driver.getTitle());
            } else {
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
                File destFile = new File("screenshots/" + fileName);
                destFile.getParentFile().mkdirs();
                Files.copy(screenshot.toPath(), destFile.toPath());
                log.info("📸 Screenshot saved: {}", destFile.getPath());
            }
        } catch (Exception e) {
            log.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }

    // ── Helper to build common HttpHeaders ──────────────────────────────────
    private HttpHeaders buildHeaders(String cookieHeader, String csrfToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", cookieHeader);
        headers.set("x-csrftoken", csrfToken);
        headers.set("accept", "application/json, text/plain, */*");
        headers.set("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36");
        headers.set("referer", "https://www.instahyre.com/candidate/opportunities/?matching=true");
        headers.set("origin", "https://www.instahyre.com");
        return headers;
    }

    // ── Helper to get cookie map from driver ─────────────────────────────────
    private Map<String, String> getCookieMap() {
        return driver.manage().getCookies().stream()
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));
    }

    // ── Helper to build cookie header string ─────────────────────────────────
    private String buildCookieHeader(Map<String, String> cookieMap) {
        return cookieMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    public List<JobDTO> scrapeJobs() {
        if (!isLoggedIn) {
            throw new IllegalStateException("Must be logged in before scraping jobs");
        }

        try {
            log.info("=== SCRAPING OPPORTUNITY JOBS ===");

            Map<String, String> cookieMap = getCookieMap();
            String cookieHeader = buildCookieHeader(cookieMap);
            String csrfToken = cookieMap.getOrDefault("csrftoken", "");

            // ✅ Use RestTemplate with raw URI to avoid encoding issues
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders(cookieHeader, csrfToken));

            List<JobDTO> allJobs = new ArrayList<>();
            int offset = 0;
            int limit = 30;
            int currentPage = 0;
            int maxPages = 50; // Configurable max pages to prevent infinite loops
            boolean hasMore = true;

            while (hasMore) {
                String fullUrl = "https://www.instahyre.com/api/v1/candidate_opportunity" +
                        "?company_size=&industry_type=&interest_facet=0&job_type=" +
                        "&limit=" + limit + "&location=&offset=" + offset;

                log.info("🔄 [Page {}] Fetching opportunity jobs at offset {}", currentPage + 1, offset);

                ResponseEntity<String> response = restTemplate.exchange(
                        new URI(fullUrl),
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                String json = response.getBody();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);

                if (offset == 0) {
                    log.info("Response preview: {}",
                            Objects.requireNonNull(json).substring(0, Math.min(300, json.length())));
                }

                JsonNode jobsNode = root.get("objects");
                if (jobsNode == null) jobsNode = root.get("results");

                if (jobsNode == null || !jobsNode.isArray() || jobsNode.isEmpty()) {
                    log.info("❌ No more opportunity jobs at page {}", currentPage + 1);
                    break;
                }

                int pageCount = 0;
                for (JsonNode opp : jobsNode) {
                    try {
                        // ✅ Fields are FLAT — no nested "job" object
                        String jobId = opp.get("id").asText();
                        String company = opp.get("employer").get("company_name").asText();
                        String title = opp.get("title").asText(); // flat, not opp.get("job").get("candidate_title")

                        List<String> skills = new ArrayList<>();
                        JsonNode keywords = opp.get("keywords"); // flat, not opp.get("job").get("keywords")
                        if (keywords != null && keywords.isArray()) {
                            for (JsonNode kw : keywords) skills.add(kw.asText());
                        }

                        allJobs.add(JobDTO.builder()
                                .id(null)
                                .jobId(jobId)
                                .title(company + " - " + title)
                                .company(company)
                                .skills(skills)
                                .source("job_search")
                                .build());
                        pageCount++;

                    } catch (Exception e) {
                        log.warn("Failed to parse job_search result: {}", e.getMessage());
                    }
                }

                log.info("✅ [Page {}] Fetched {} jobs (total collected: {})",
                         currentPage + 1, pageCount, allJobs.size());

                // Update pagination state and prepare for next page
                // MORE AGGRESSIVE: Only stop when we get 0 items on a page
                if (pageCount == 0) {
                    log.info("⏹️  Got 0 items - stopping pagination");
                    hasMore = false;
                } else {
                    log.info("➡️  Got {} items - checking for more pages...", pageCount);
                    offset += limit;
                    currentPage++;
                    hasMore = currentPage < maxPages;
                    if (hasMore) {
                        Thread.sleep(1000); // Rate limiting between pages
                    }
                }
            }

            log.info("✅ Scraped {} opportunity jobs across {} pages", allJobs.size(), currentPage + 1);
            return allJobs;

        } catch (Exception e) {
            log.error("❌ Error scraping opportunity jobs", e);
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

            Map<String, String> cookieMap = getCookieMap();
            String cookieHeader = buildCookieHeader(cookieMap);
            String csrfToken = cookieMap.getOrDefault("csrftoken", "");

            // ✅ Use RestTemplate with raw URI — avoids double-encoding of %2F etc.
            RestTemplate restTemplate = new RestTemplate();
            HttpEntity<String> entity = new HttpEntity<>(buildHeaders(cookieHeader, csrfToken));

            // ✅ Exact params from browser — do NOT modify encoding
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
            int currentPage = 0;
            int maxPages = 50; // Configurable max pages to prevent infinite loops
            boolean hasMore = true;

            while (hasMore) {
                String fullUrl = "https://www.instahyre.com/api/v1/job_search?"
                        + baseParams + "&limit=" + limit + "&offset=" + offset;

                log.info("🔄 [Page {}] Fetching job_search at offset {}", currentPage + 1, offset);

                ResponseEntity<String> response = restTemplate.exchange(
                        new URI(fullUrl), // ✅ raw URI, no re-encoding
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                String json = response.getBody();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);

                if (offset == 0) {
                    log.info("job_search preview: {}",
                            Objects.requireNonNull(json).substring(0, Math.min(500, json.length())));
                }

                JsonNode jobsNode = root.get("objects");
                if (jobsNode == null) jobsNode = root.get("results");

                if (jobsNode == null || !jobsNode.isArray() || jobsNode.isEmpty()) {
                    log.info("❌ No more job_search results at page {}", currentPage + 1);
                    break;
                }

                int pageCount = 0;
                for (JsonNode opp : jobsNode) {
                    try {
                        // Try to get job ID - could be at top level or nested under "job"
                        String jobId = null;
                        if (opp.has("job") && opp.get("job") != null && opp.get("job").has("id")) {
                            jobId = opp.get("job").get("id").asText();
                        } else if (opp.has("id")) {
                            jobId = opp.get("id").asText();
                        }

                        if (jobId == null) {
                            log.debug("Skipping job without ID");
                            continue;
                        }

                        String company = null;
                        if (opp.has("employer") && opp.get("employer") != null && opp.get("employer").has("company_name")) {
                            company = opp.get("employer").get("company_name").asText();
                        }

                        if (company == null || company.isEmpty()) {
                            log.debug("Skipping job without company");
                            continue;
                        }

                        // Get title - could be at top level or nested
                        String title = null;
                        if (opp.has("job") && opp.get("job") != null && opp.get("job").has("candidate_title")) {
                            title = opp.get("job").get("candidate_title").asText();
                        } else if (opp.has("title")) {
                            title = opp.get("title").asText();
                        }

                        if (title == null || title.isEmpty()) {
                            log.debug("Skipping job without title");
                            continue;
                        }

                        List<String> skills = new ArrayList<>();
                        JsonNode keywords = null;
                        if (opp.has("job") && opp.get("job") != null && opp.get("job").has("keywords")) {
                            keywords = opp.get("job").get("keywords");
                        } else if (opp.has("keywords")) {
                            keywords = opp.get("keywords");
                        }

                        if (keywords != null && keywords.isArray()) {
                            for (JsonNode kw : keywords) skills.add(kw.asText());
                        }

                        allJobs.add(JobDTO.builder()
                                .id(null)
                                .jobId(jobId)
                                .title(company + " - " + title)
                                .company(company)
                                .skills(skills)
                                .source("job_search")
                                .build());
                        pageCount++;

                    } catch (Exception e) {
                        log.warn("Failed to parse job_search result: {}", e.getMessage());
                    }
                }

                log.info("✅ [Page {}] Fetched {} jobs (total collected: {})",
                         currentPage + 1, pageCount, allJobs.size());

                // Update pagination state and prepare for next page
                // MORE AGGRESSIVE: Only stop when we get 0 items on a page
                if (pageCount == 0) {
                    log.info("⏹️  Got 0 items - stopping pagination");
                    hasMore = false;
                } else {
                    log.info("➡️  Got {} items - checking for more pages...", pageCount);
                    offset += limit;
                    currentPage++;
                    hasMore = currentPage < maxPages;
                    if (hasMore) {
                        Thread.sleep(1000); // Rate limiting between pages
                    }
                }
            }

            log.info("✅ job_search scraped {} total jobs across {} pages", allJobs.size(), currentPage + 1);
            return allJobs;

        } catch (Exception e) {
            log.error("❌ Error in scrapeJobSearch", e);
            return new ArrayList<>();
        }
    }

    public boolean applyToJob(JobDTO job) {
        try {
            log.info("Attempting to apply: {} (source: {})", job.getTitle(), job.getSource());

            Map<String, String> cookieMap = getCookieMap();
            String cookieHeader = buildCookieHeader(cookieMap);
            String csrfToken = cookieMap.getOrDefault("csrftoken", "");

            // Build payload based on source
            Map<String, Object> body = new HashMap<>();
            body.put("is_interested", true);
            body.put("is_activity_page_job", false);

            if ("job_search".equals(job.getSource())) {
                body.put("id", null);
                body.put("job_id", Long.parseLong(job.getJobId()));
            } else {
                body.put("id", job.getId());
            }

            log.info("Payload: {}", body);

            // ✅ Use RestClient for POST (no encoding issue with POST body)
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
            log.error("❌ Failed: {} - {} - {}", job.getTitle(), e.getStatusCode(), responseBody);
            return false;
        } catch (Exception e) {
            log.error("❌ Failed: {} - {}", job.getTitle(), e.getMessage());
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