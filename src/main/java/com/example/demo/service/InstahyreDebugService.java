package com.example.demo.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Debug tool to inspect Instahyre login page structure
 * Run this to see what elements are actually available on the page
 */
@Service
@Slf4j
public class InstahyreDebugService {

    public void inspectLoginPage() {
        WebDriver driver = null;
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            // Run in visible mode for debugging
            // options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            driver = new ChromeDriver(options);

            log.info("Opening Instahyre login page...");
            driver.get("https://www.instahyre.com/login");

            // Wait for page to load
            Thread.sleep(5000);

            log.info("=== PAGE INSPECTION ===");
            log.info("Page Title: {}", driver.getTitle());
            log.info("Current URL: {}", driver.getCurrentUrl());

            // Find all links
            log.info("\n=== ALL LINKS ON PAGE ===");
            List<WebElement> links = driver.findElements(By.tagName("a"));
            for (int i = 0; i < links.size() && i < 20; i++) {
                WebElement link = links.get(i);
                String text = link.getText();
                String href = link.getAttribute("href");
                if (!text.isEmpty() || (href != null && !href.isEmpty())) {
                    log.info("Link {}: Text='{}', Href='{}'", i, text, href);
                }
            }

            // Find all buttons
            log.info("\n=== ALL BUTTONS ON PAGE ===");
            List<WebElement> buttons = driver.findElements(By.tagName("button"));
            for (int i = 0; i < buttons.size() && i < 20; i++) {
                WebElement button = buttons.get(i);
                String text = button.getText();
                String className = button.getAttribute("class");
                log.info("Button {}: Text='{}', Class='{}'", i, text, className);
            }

            // Find all input fields
            log.info("\n=== ALL INPUT FIELDS ===");
            List<WebElement> inputs = driver.findElements(By.tagName("input"));
            for (int i = 0; i < inputs.size(); i++) {
                WebElement input = inputs.get(i);
                String type = input.getAttribute("type");
                String name = input.getAttribute("name");
                String placeholder = input.getAttribute("placeholder");
                log.info("Input {}: Type='{}', Name='{}', Placeholder='{}'", i, type, name, placeholder);
            }

            // Search for SSO-related elements
            log.info("\n=== SEARCHING FOR SSO ELEMENTS ===");
            try {
                List<WebElement> ssoElements = driver.findElements(By.xpath("//*[contains(text(), 'SSO') or contains(text(), 'LinkedIn') or contains(@href, 'linkedin')]"));
                log.info("Found {} SSO-related elements", ssoElements.size());
                for (int i = 0; i < ssoElements.size(); i++) {
                    WebElement elem = ssoElements.get(i);
                    log.info("SSO Element {}: Tag='{}', Text='{}', Class='{}'",
                            i, elem.getTagName(), elem.getText(), elem.getAttribute("class"));
                }
            } catch (Exception e) {
                log.warn("No SSO elements found");
            }

            // Print page source (first 5000 chars)
            log.info("\n=== PAGE SOURCE (first 5000 chars) ===");
            String pageSource = driver.getPageSource();
            log.info(pageSource.substring(0, Math.min(5000, pageSource.length())));

            // Keep browser open for manual inspection
            log.info("\n=== Browser will stay open for 30 seconds for manual inspection ===");
            Thread.sleep(30000);

        } catch (Exception e) {
            log.error("Error during inspection", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}