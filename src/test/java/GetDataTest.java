import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GetDataTest extends BaseTest {

    @Test
    @Disabled
    public void getFieldNames(){
        driver.get("https://www3.wipo.int/madrid/monitor/en/showData.jsp?ID=ROM.1864832&DES=1");

        // Находим все абзацы на странице
        List<WebElement> inidTexts = driver.findElements(By.className("date"));
        int num = 0;

        // Проходимся по каждому абзацу и выводим его текст
        for (WebElement inidText : inidTexts) {
            System.out.println(num + "---");
            System.out.println(inidText.getText());
            num++;
        }
        driver.close();
    }

    @Test
    public void getDataFromWipo () throws IOException {
        String prefix = "18648";
        for (int i = 10; i <= 20; i++) {
            String suffix = String.format("%02d", i);
            String fullNumber = prefix + suffix;

            try {
                allData = companyCatalogPage.getCompanyData(fullNumber, "text", wipoLink);
                List<WebElement> sliceAllData = allData.subList(7,17);
                List<String> keys = companyCatalogPage.wipoKeys.subList(0, companyCatalogPage.wipoKeys.size() - 2);
                Map<String, String> dataMap = companyCatalogPage.getDataMap(keys, sliceAllData);

                List<WebElement> date = driver.findElements(By.className("date"));

                String regDate = date.get(2).getText();   // registrationDate
                String expDate = date.get(3).getText();   // expirationDate
                dataMap.put("registrationDate", regDate);
                dataMap.put("expirationDate", expDate);

                String appNum = companyCatalogPage.getApplicationNumber_(allData);
                File outputFile = companyCatalogPage.generatePathToJson(appNum, "wipo");

                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                mapper.writeValue(outputFile, dataMap);

                System.out.println("JSON saved to: " + outputFile.getAbsolutePath());
            } catch (NullPointerException nullPointerException){

            }
        }
    }

    @Test
    public void getDataFromAipa () throws IOException {
        String prefix = "202421";
            for (int i = 10; i <= 20; i++) {
                String suffix = String.format("%02d", i);
                String fullNumber = prefix + suffix;

                companyCatalogPage = new CompanyCatalogPage(driver);
                try {
                    allData = companyCatalogPage.getCompanyData(fullNumber, "data", aipaLink);
                    List<String> keys = companyCatalogPage.aipaKeys;
                    Map<String, String> dataMap = companyCatalogPage.getDataMap(keys, allData);
                    String appNum = companyCatalogPage.getApplicationNumber(dataMap);
                    File outputFile = companyCatalogPage.generatePathToJson(appNum, "aipa");

                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    mapper.writeValue(outputFile, dataMap);

                    System.out.println("JSON saved to: " + outputFile.getAbsolutePath());
                } catch (NullPointerException nullPointerException){

                }
            }
    }
}
