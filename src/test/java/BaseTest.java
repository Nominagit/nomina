import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;

public class BaseTest {
    public WebDriver driver;
    CompanyCatalogPage companyCatalogPage;
    List<WebElement> allData;
    String aipaLink = CompanyCatalogPage.aipaBaseLink;
    String wipoLink = CompanyCatalogPage.wipoBaseLink;

    @BeforeEach
    public void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-gpu",
                "--window-size=1920,1080");
        driver = new ChromeDriver(options);
        companyCatalogPage = new CompanyCatalogPage(driver);
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

}
