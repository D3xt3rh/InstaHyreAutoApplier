package com.example.demo.service;

import com.example.demo.config.InstahyreConfig;
import com.example.demo.dto.JobDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bonigarcia.wdm.WebDriverManager;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
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
    private boolean isLoggedIn = false;

    public void initDriver() {
        if (driver == null) {
            try {
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();

                // Enable for debugging - comment out to see browser
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
     * Try to login automatically
     */
    public void login() {
        if (isLoggedIn) {
            log.info("Already logged in, skipping login");
            return;
        }

        try {
            log.info("=== STARTING LOGIN PROCESS ===");
            driver.get("https://www.instahyre.com/login");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            Thread.sleep(3000);

            takeScreenshot("01-login-page-loaded");
            logPageInfo();

            boolean loginSuccess = false;

            // Strategy 1: Look for email/password login first (most reliable)
            log.info("Trying Strategy 1: Direct email/password login");
            loginSuccess = tryDirectEmailLogin(wait);

            if (!loginSuccess) {
                log.info("Trying Strategy 2: LinkedIn/SSO buttons");
                loginSuccess = tryLinkedInButton(wait);
            }

            if (!loginSuccess) {
                log.error("=== ALL LOGIN STRATEGIES FAILED ===");
                log.error("Please check the screenshot at: screenshots/01-login-page-loaded-*.png");
                log.error("You have two options:");
                log.error("1. Check the screenshot to see what's on the page");
                log.error("2. Use manual cookie-based login (see loginWithManualCookies method)");
                throw new RuntimeException("All login strategies failed. Check screenshots and logs.");
            }

            isLoggedIn = true;
            log.info("=== LOGIN SUCCESSFUL ===");

        } catch (Exception e) {
            log.error("Login failed", e);
            takeScreenshot("99-login-final-error");
            isLoggedIn = false;
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }

    /**
     * Alternative: Use cookies from a manual login session
     *
     * Steps to use this:
     * 1. Login manually to Instahyre in Chrome
     * 2. Open Developer Tools (F12) -> Application -> Cookies
     * 3. Copy the values of 'sessionid' and 'csrftoken'
     * 4. Update application.yml with these values
     * 5. Call this method instead of login()
     */
    public void loginWithManualCookies(String sessionId, String csrfToken) {
        try {
            log.info("Using manual cookie-based authentication");
            driver.get("https://www.instahyre.com");

            // Add cookies
            driver.manage().addCookie(new Cookie("sessionid", sessionId));
            driver.manage().addCookie(new Cookie("csrftoken", csrfToken));

            // Refresh to apply cookies
            driver.navigate().refresh();
            Thread.sleep(2000);

            // Verify login worked
            driver.get("https://www.instahyre.com/candidate/opportunities");
            Thread.sleep(2000);

            if (!driver.getCurrentUrl().contains("login")) {
                isLoggedIn = true;
                log.info("Cookie-based login successful!");
            } else {
                throw new RuntimeException("Cookie-based login failed - cookies may be expired");
            }

        } catch (Exception e) {
            log.error("Cookie-based login failed", e);
            throw new RuntimeException("Cookie-based login failed", e);
        }
    }

    private boolean tryDirectEmailLogin(WebDriverWait wait) {
        try {
            // Look for email input field
            WebElement emailField = null;
            String[] emailSelectors = {
                    "input[type='email']",
                    "input[name='email']",
                    "input[placeholder*='email' i]",
                    "input[placeholder*='Email' i]",
                    "input[id='email']",
                    "input[id='username']"
            };

            for (String selector : emailSelectors) {
                try {
                    emailField = driver.findElement(By.cssSelector(selector));
                    if (emailField.isDisplayed()) {
                        log.info("Found email field with selector: {}", selector);
                        break;
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }

            if (emailField == null) {
                log.warn("No email field found on page");
                return false;
            }

            // Look for password field
            WebElement passwordField = driver.findElement(By.cssSelector("input[type='password']"));

            // Look for submit button
            WebElement submitButton = null;
            String[] buttonSelectors = {
                    "button[type='submit']",
                    "input[type='submit']",
                    "button:contains('Login')",
                    "button:contains('Sign in')",
                    "button.submit",
                    "button.login-btn"
            };

            for (String selector : buttonSelectors) {
                try {
                    submitButton = driver.findElement(By.cssSelector(selector));
                    if (submitButton.isDisplayed()) {
                        log.info("Found submit button with selector: {}", selector);
                        break;
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }

            if (submitButton == null) {
                // Try finding any button
                List<WebElement> buttons = driver.findElements(By.tagName("button"));
                for (WebElement btn : buttons) {
                    String text = btn.getText().toLowerCase();
                    if (text.contains("login") || text.contains("sign in") || text.contains("submit")) {
                        submitButton = btn;
                        break;
                    }
                }
            }

            if (submitButton == null) {
                log.warn("No submit button found");
                return false;
            }

            // Fill in credentials
            log.info("Filling in email and password");
            emailField.clear();
            emailField.sendKeys(config.getUsername());

            passwordField.clear();
            passwordField.sendKeys(config.getPassword());

            takeScreenshot("02-credentials-entered");

            submitButton.click();
            log.info("Clicked submit button");

            // Wait for login to complete
            Thread.sleep(5000);
            takeScreenshot("03-after-submit");

            // Check if login was successful
            String currentUrl = driver.getCurrentUrl();
            log.info("Current URL after login attempt: {}", currentUrl);

            if (!currentUrl.contains("login")) {
                log.info("Direct email login successful!");
                return true;
            }

            // Check if there's an error message
            try {
                WebElement errorMsg = driver.findElement(By.cssSelector(".error, .alert-danger, [class*='error']"));
                log.warn("Login error message: {}", errorMsg.getText());
            } catch (Exception e) {
                // No error message found
            }

            return false;

        } catch (Exception e) {
            log.warn("Direct email login failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean tryLinkedInButton(WebDriverWait wait) {
        try {
            log.info("Looking for LinkedIn/SSO buttons");

            String[] selectors = {
                    "a[href*='linkedin.com/oauth']",
                    "a[href*='linkedin']",
                    "button[data-provider='linkedin']",
                    ".linkedin-login",
                    "#linkedin-login",
                    "a:contains('LinkedIn')",
                    "a:contains('SSO')",
                    "button:contains('LinkedIn')",
                    "button:contains('SSO')"
            };

            for (String selector : selectors) {
                try {
                    WebElement button = driver.findElement(By.cssSelector(selector));
                    if (button.isDisplayed()) {
                        log.info("Found LinkedIn button with selector: {}", selector);
                        button.click();
                        return handleLinkedInLogin(wait);
                    }
                } catch (Exception e) {
                    // Try next selector
                }
            }

            // Try XPath as last resort
            String[] xpaths = {
                    "//a[contains(text(), 'LinkedIn')]",
                    "//a[contains(text(), 'SSO')]",
                    "//button[contains(text(), 'LinkedIn')]",
                    "//button[contains(text(), 'SSO')]",
                    "//a[contains(@class, 'linkedin')]",
                    "//button[contains(@class, 'linkedin')]"
            };

            for (String xpath : xpaths) {
                try {
                    WebElement button = driver.findElement(By.xpath(xpath));
                    if (button.isDisplayed()) {
                        log.info("Found LinkedIn button with XPath: {}", xpath);
                        button.click();
                        return handleLinkedInLogin(wait);
                    }
                } catch (Exception e) {
                    // Try next xpath
                }
            }

            log.warn("No LinkedIn/SSO button found");
            return false;

        } catch (Exception e) {
            log.warn("LinkedIn button strategy failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean handleLinkedInLogin(WebDriverWait wait) throws InterruptedException {
        wait.until(ExpectedConditions.urlContains("linkedin.com"));
        log.info("Redirected to LinkedIn");

        takeScreenshot("04-linkedin-page");

        WebElement emailField = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("username"))
        );
        WebElement passwordField = driver.findElement(By.id("password"));
        WebElement signInButton = driver.findElement(By.xpath("//button[@type='submit']"));

        emailField.sendKeys(config.getUsername());
        passwordField.sendKeys(config.getPassword());

        takeScreenshot("05-linkedin-credentials-entered");

        signInButton.click();
        log.info("Submitted LinkedIn credentials");

        wait.until(ExpectedConditions.urlContains("instahyre.com"));
        Thread.sleep(5000);

        takeScreenshot("06-after-linkedin-redirect");

        return true;
    }

    private void logPageInfo() {
        try {
            log.info("=== PAGE INFORMATION ===");
            log.info("Title: {}", driver.getTitle());
            log.info("URL: {}", driver.getCurrentUrl());

            // Log all visible buttons
            List<WebElement> buttons = driver.findElements(By.tagName("button"));
            log.info("Found {} buttons on page:", buttons.size());
            for (int i = 0; i < Math.min(buttons.size(), 10); i++) {
                WebElement btn = buttons.get(i);
                try {
                    if (btn.isDisplayed()) {
                        log.info("  Button {}: text='{}', class='{}'",
                                i, btn.getText(), btn.getAttribute("class"));
                    }
                } catch (Exception e) {
                    // Skip this button
                }
            }

            // Log all visible links
            List<WebElement> links = driver.findElements(By.tagName("a"));
            log.info("Found {} links on page:", links.size());
            for (int i = 0; i < Math.min(links.size(), 10); i++) {
                WebElement link = links.get(i);
                try {
                    if (link.isDisplayed()) {
                        log.info("  Link {}: text='{}', href='{}'",
                                i, link.getText(), link.getAttribute("href"));
                    }
                } catch (Exception e) {
                    // Skip this link
                }
            }

            // Log all input fields
            List<WebElement> inputs = driver.findElements(By.tagName("input"));
            log.info("Found {} input fields:", inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                WebElement input = inputs.get(i);
                try {
                    log.info("  Input {}: type='{}', name='{}', placeholder='{}'",
                            i, input.getAttribute("type"), input.getAttribute("name"),
                            input.getAttribute("placeholder"));
                } catch (Exception e) {
                    // Skip this input
                }
            }

        } catch (Exception e) {
            log.warn("Failed to log page info: {}", e.getMessage());
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
                    .build();

            String json = restClient.get()
                    .uri("/api/v1/candidate_opportunity?limit=30&offset=0")
                    .retrieve()
                    .body(String.class);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.get("results");

            List<JobDTO> jobs = new ArrayList<>();
            for (JsonNode opp : results) {
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
                    jobs.add(job);
                } catch (Exception e) {
                    log.warn("Failed to parse job opportunity", e);
                }
            }

            log.info("Successfully scraped {} jobs", jobs.size());
            return jobs;
        } catch (Exception e) {
            log.error("Error fetching jobs via API", e);
            return new ArrayList<>();
        }
    }

    public boolean applyToJob(JobDTO job) {
        try {
            log.info("Attempting to apply to job: {}", job.getTitle());

            driver.get("https://www.instahyre.com/candidate/opportunities");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            Thread.sleep(2000);

            boolean clicked = false;

            try {
                String xpath = String.format(
                        "//div[contains(text(), '%s')]//ancestor::div[contains(@class, 'opportunity') or contains(@class, 'job')]//button[contains(text(), 'View') or contains(text(), 'Apply')]",
                        job.getCompany()
                );
                WebElement viewButton = wait.until(
                        ExpectedConditions.elementToBeClickable(By.xpath(xpath))
                );
                viewButton.click();
                clicked = true;
                log.info("Clicked view/apply button for job: {}", job.getTitle());
            } catch (Exception e1) {
                log.warn("Strategy 1 failed, trying alternative approach");

                try {
                    List<WebElement> opportunities = driver.findElements(
                            By.xpath("//div[contains(@class, 'opportunity') or contains(@class, 'job-card')]")
                    );

                    for (WebElement opp : opportunities) {
                        if (opp.getText().contains(job.getCompany())) {
                            WebElement button = opp.findElement(
                                    By.xpath(".//button[contains(text(), 'View') or contains(text(), 'Apply')]")
                            );
                            button.click();
                            clicked = true;
                            log.info("Found and clicked button using card search");
                            break;
                        }
                    }
                } catch (Exception e2) {
                    log.error("Strategy 2 also failed", e2);
                }
            }

            if (clicked) {
                Thread.sleep(2000);

                try {
                    WebElement applyButton = driver.findElement(
                            By.xpath("//button[contains(text(), 'Apply') or contains(text(), 'Confirm')]")
                    );
                    applyButton.click();
                    log.info("Clicked final apply button");
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.info("No additional apply button found, application may be complete");
                }

                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Failed to apply to job: {}", job.getTitle(), e);
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