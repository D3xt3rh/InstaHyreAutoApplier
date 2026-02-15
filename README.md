# Auto Applier for Instahyre

A Spring Boot application that automates job applications on Instahyre, a job search platform. It uses Selenium WebDriver to handle login via LinkedIn SSO and applies to jobs based on configurable keywords.

## Features

- **Automated Login**: Logs into Instahyre using LinkedIn Single Sign-On (SSO).
- **Job Scraping**: Retrieves job listings from Instahyre's API.
- **Keyword Filtering**: Applies to jobs that match specified skill keywords.
- **Scheduled Execution**: Runs automatically every hour via Spring Scheduler.
- **REST API**: Provides endpoints to manually fetch jobs or trigger the auto-apply process.

## Prerequisites

- Java 17 or higher
- Gradle (for building the project)
- Google Chrome browser (for Selenium WebDriver)
- LinkedIn account with Instahyre access

## Installation

1. Clone the repository:
   ```
   git clone <repository-url>
   cd demo
   ```

2. Build the project:
   ```
   ./gradlew build
   ```

## Configuration

Configure the application using environment variables or `application.yml`:

```yaml
instahyre:
  username: ${INSTAHYRE_USERNAME}  # Your LinkedIn email
  password: ${INSTAHYRE_PASSWORD}  # Your LinkedIn password
  keywords:
    - Java
    - Spring Boot
    - Springboot
```

Set the environment variables:
- `INSTAHYRE_USERNAME`: Your LinkedIn email address
- `INSTAHYRE_PASSWORD`: Your LinkedIn password

Modify the `keywords` list in `application.yml` to match the skills you want to target.

## Usage

### Running the Application

Start the application:
```
./gradlew bootRun
```

The application will start on port 8080 by default.

### API Endpoints

- **GET /api/jobs**: Manually fetch and return a list of scraped jobs.
- **POST /api/jobs/apply**: Trigger the auto-apply process and return a list of jobs applied to.

### Scheduled Auto-Apply

The application automatically runs the auto-apply process every hour. This can be disabled by commenting out `@EnableScheduling` in `AutoApplierForInstahyreApplication.java`.

## How It Works

1. **Login**: Uses Selenium to navigate to Instahyre login page and authenticate via LinkedIn SSO.
2. **Scrape Jobs**: Fetches job opportunities using Instahyre's API with authenticated session cookies.
3. **Filter Jobs**: Checks if job skills match any of the configured keywords.
4. **Apply**: Uses Selenium to click the apply button for matching jobs.

## Dependencies

- Spring Boot
- Selenium WebDriver
- WebDriverManager (for ChromeDriver management)
- Lombok (for boilerplate code reduction)

## Security Note

This application handles sensitive information like login credentials. Ensure environment variables are properly secured and not committed to version control. Use strong, unique passwords and consider enabling two-factor authentication on your LinkedIn account.

## Disclaimer

Automated job applications may violate platform terms of service. Use this tool responsibly and at your own risk. Always review platform policies before using automation tools.
