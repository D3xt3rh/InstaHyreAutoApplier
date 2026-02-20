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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            if (currentUrl.contains("login")) {
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
                destFile.getParentFile().mkdirs();
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

            // Get cookies from Selenium
            Map<String, String> cookieMap = driver.manage().getCookies().stream()
                    .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));

            log.info("Using {} cookies for API request", cookieMap.size());

            String cookieHeader = cookieMap.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; "));

            String csrfToken = cookieMap.getOrDefault("csrftoken", "");
            log.info("CSRF token present: {}", !csrfToken.isEmpty());

            RestClient restClient = RestClient.builder()
                    .baseUrl("https://www.instahyre.com")
                    .defaultHeader("Cookie", cookieHeader)
                    .defaultHeader("x-csrftoken", csrfToken)
                    .defaultHeader("accept", "application/json, text/plain, */*")
                    .defaultHeader("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
                    .defaultHeader("referer", "https://www.instahyre.com/candidate/opportunities")
                    .build();

            log.info("Making API request to fetch jobs...");
            List<JobDTO> allJobs = new ArrayList<>();
            String url = "/api/v1/candidate_opportunity?limit=30";

            while (url != null) {
                log.info("Fetching page: {}", url);
                String json = restClient.get()
                        .uri(url)
                        .retrieve()
                        .body(String.class);

                log.info("API response received, parsing JSON...");
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(json);

                // Try both 'results' and 'objects' fields (API format can vary)
                JsonNode jobsNode = root.get("results");
                if (jobsNode == null) {
                    jobsNode = root.get("objects");
                }

                if (jobsNode == null) {
                    log.error("No 'results' or 'objects' field in API response");
                    log.error("Response preview: {}", json.substring(0, Math.min(500, json.length())));
                    break;
                }

                for (JsonNode opp : jobsNode) {
                    try {
                        String opportunityId = opp.get("id").asText();
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
                                .id(opportunityId)
                                .title(fullTitle)
                                .company(company)
                                .skills(skills)
                                .build();
                        allJobs.add(job);
                    } catch (Exception e) {
                        log.warn("Failed to parse job opportunity", e);
                    }
                }

                // Check for next page
                JsonNode next = root.get("next");
                if (next != null && !next.isNull()) {
                    String nextUrl = next.asText();
                    url = nextUrl.replace("https://www.instahyre.com", "");
                } else {
                    url = null;
                }
            }

            log.info("✅ Successfully scraped {} jobs", allJobs.size());
            return allJobs;

        } catch (Exception e) {
            log.error("❌ Error fetching jobs via API", e);
            return new ArrayList<>();
        }
    }

    public boolean applyToJob(JobDTO job) {
        try {
            log.info("Attempting to apply to job: {} (ID: {})", job.getTitle(), job.getId());

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Navigate to the job page
            driver.get("https://www.instahyre.com/job/" + job.getId());
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Try to find and click the apply button
            boolean clicked = false;

            // Strategy 1: Button with text "Apply"
            try {
                WebElement applyButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Apply')]")));
                applyButton.click();
                clicked = true;
                log.info("Clicked apply button for job: {}", job.getTitle());
            } catch (Exception e1) {
                log.debug("Strategy 1 failed for job '{}': {}", job.getTitle(), e1.getMessage());
                try {
                    // Log all buttons on the page for debugging
                    List<WebElement> allButtons = driver.findElements(By.tagName("button"));
                    log.info("Found {} buttons on page for job '{}':", allButtons.size(), job.getTitle());
                    for (int i = 0; i < Math.min(allButtons.size(), 10); i++) {
                        WebElement btn = allButtons.get(i);
                        log.info("Button {}: text='{}', class='{}', id='{}'", i, btn.getText(), btn.getAttribute("class"), btn.getAttribute("id"));
                    }
                } catch (Exception logE) {
                    log.warn("Could not log buttons: {}", logE.getMessage());
                }
            }

            if (!clicked) {
                // Strategy 2: Button with text "Apply Now"
                try {
                    WebElement applyButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Apply Now')]")));
                    applyButton.click();
                    clicked = true;
                    log.info("Clicked 'Apply Now' button for job: {}", job.getTitle());
                } catch (Exception e2) {
                    log.debug("Strategy 2 failed for job '{}': {}", job.getTitle(), e2.getMessage());
                }
            }

            if (!clicked) {
                // Strategy 3: Any button containing 'Apply' case insensitive
                try {
                    WebElement applyButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'apply')]")));
                    applyButton.click();
                    clicked = true;
                    log.info("Clicked apply button (case insensitive) for job: {}", job.getTitle());
                } catch (Exception e3) {
                    log.debug("Strategy 3 failed for job '{}': {}", job.getTitle(), e3.getMessage());
                }
            }

            if (!clicked) {
                // Strategy 4: Link with text containing 'Apply'
                try {
                    WebElement applyLink = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(), 'Apply') or contains(@href, 'apply')]")));
                    applyLink.click();
                    clicked = true;
                    log.info("Clicked apply link for job: {}", job.getTitle());
                } catch (Exception e4) {
                    log.debug("Strategy 4 failed for job '{}': {}", job.getTitle(), e4.getMessage());
                }
            }

            // If clicked, wait for modal or confirmation
            if (clicked) {
                try {
                    // Wait for modal to appear and click final apply
                    WebElement finalApplyButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(), 'Apply') and contains(@class, 'btn-success')]")));
                    finalApplyButton.click();
                    log.info("Clicked final apply button");

                    // Wait for modal to close
                    wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".application-modal-backdrop")));
                    log.info("Modal closed after apply");
                } catch (Exception e) {
                    log.warn("Could not complete apply process for job '{}': {}", job.getTitle(), e.getMessage());
                    takeScreenshot("apply_error_" + job.getTitle().replaceAll("[^a-zA-Z0-9]", "_"));
                    return false;
                }
            } else {
                log.warn("Could not find apply button for job: {}", job.getTitle());
                takeScreenshot("no_apply_button_" + job.getTitle().replaceAll("[^a-zA-Z0-9]", "_"));
                return false;
            }

            log.info("Successfully applied to: {} (Total applied: ?)", job.getTitle());
            return true;

        } catch (Exception e) {
            log.error("❌ Failed to apply to job: {}", job.getTitle(), e.getMessage());
            takeScreenshot("apply_failure_" + job.getTitle().replaceAll("[^a-zA-Z0-9]", "_"));
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