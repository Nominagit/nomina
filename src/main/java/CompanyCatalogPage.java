import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

public class CompanyCatalogPage {
    WebDriver driver;
    static String aipaBaseLink = "https://old.aipa.am/search_mods/tm_database/view_item.php?id=";
    static String wipoBaseLink = "https://www3.wipo.int/madrid/monitor/en/showData.jsp?ID=ROM.";

    public CompanyCatalogPage(WebDriver driver) {
        this.driver = driver;
    }

    List<String> aipaKeys = Arrays.asList(
            "markName",                     // (540) նշանի անվանումը
            "registrationSerialNumber",     // (111) գրանցման հերթական համարը
            "registrationYear",             // (151) գրանցման թվականը
            "expectedRegistrationCompletion",// (181) գրանցման գործողության ենթադրյալ ավարտը
            "applicationNumber",            // (210) հայտի համարը
            "applicationFilingYear",        // (220) հայտի ներկայացման թվականը
            "applicationPublicationYear",   // (442) Հայտի հրապարակման թվականը
            "goodsAndServices",             // (511) ապրանքների և (կամ) ծառայությունների ցանկը
            "colorAndCombination",          // (591) գույնը և գունային համակցությունը
            "proprietor",                   // (730) նշանի իրավատերը, հասցեն, երկրի կոդը
            "representative"                // (750) ներկայացուցիչ
    );
    List<String> wipoKeys = Arrays.asList(
            "holderInfo",
            "establishmentJurisdiction",
            "legalInfo",
            "representativeInfo",
            "markType",
            "viennaClass",
            "niceClass",
            "basicApplication",
            "basicRegistration",
            "madridDesignation",
            "registrationDate",
            "expirationDate"
    );

    public List<WebElement> getCompanyData(String linkId, String searchingData, String dataLink) {
        driver.get(dataLink + linkId);
        return driver.findElements(By.className(searchingData));
    }
    public Map<String, String> getDataMap(List<String> keys, List<WebElement> allData) {
        Map<String, String> dataMap = new LinkedHashMap<>();
        for (int k = 0; k < keys.size() && k < allData.size(); k++) {
            String value = allData.get(k).getText().trim();
            dataMap.put(keys.get(k), value);
        }
        return dataMap;
    }
    public String getApplicationNumber(Map<String, String> dataMap ){
        String appNum = dataMap.get("applicationNumber")
                .replaceAll("[^a-zA-Z0-9_-]", "");
        if (appNum.isEmpty()) {
            appNum = "unknown_app";
        }
        return appNum;
    }
    public String getApplicationNumber_(List<WebElement> allData){
        WebElement el = allData.get(0);
        String appNum = el.getText()
                .replaceAll("\\D+", "");
        if (appNum.isEmpty()) {
            appNum = "unknown_app";
        }
        return appNum;
    }
    public File generatePathToJson(String applicationNumber, String linkName){
        String projectDir = System.getProperty("user.dir");
        File outputFile = Paths.get(
                projectDir,
                "src", "test", "resources", "json", "success", linkName,
                applicationNumber + ".json"
        ).toFile();
        return outputFile;
    }
    public File generatePathToJson_(String applicationNumber, String linkName){
        String projectDir = System.getProperty("user.dir");
        File outputFile = Paths.get(
                projectDir,
                "src", "test", "resources", "json", "error", linkName,
                applicationNumber + ".json"
        ).toFile();
        return outputFile;
    }
    public File generatePathToImage(String linkName, String linkId){
        String projectDir = System.getProperty("user.dir");
        File outputFile = Paths.get(
                projectDir,
                "src", "test", "resources", "image", linkName,
                linkId + ".jpg"
        ).toFile();
        return outputFile;
    }
    public void saveDataToJson(String appNum, String dataName, Map<String, String> dataMap) throws IOException {
        File outputFile = generatePathToJson(appNum, dataName);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile, dataMap);

        System.out.println("JSON saved to: " + outputFile.getAbsolutePath());
    }
    public void saveDataToJson_(String appNum, String dataName, Map<String, String> dataMap) throws IOException {
        File outputFile = generatePathToJson_(appNum, dataName);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile, dataMap);

        System.out.println("JSON saved to: " + outputFile.getAbsolutePath());
    }
    public String getCompanyImageSrc(String dataLink, String linkId) {
        driver.get(dataLink + linkId);
        return driver.findElement(By.xpath("//img[contains(@src, '"+ linkId +"')]"))
                .getAttribute("src");
    }
    public void downloadImage(String dataLink, String dataName, String linkId) throws IOException {
        String imageUrl = getCompanyImageSrc(dataLink, linkId);
        File imagePath = generatePathToImage(dataName, linkId);

        URL base = new URL(dataLink);
        URL imgUrl = new URL(base, imageUrl);

        BufferedImage img = ImageIO.read(imgUrl);
        ImageIO.write(img, "jpg", imagePath);
    }
    public void downloadImageWithRetry(String dataLink, String dataName, String linkId) throws IOException {
        int attempts = 0;
        while (attempts < 3) {
            try {
                downloadImage(dataLink, dataName, linkId);
                return; // всё успешно
            } catch (NoSuchElementException e) {
                attempts++;
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
        throw new NoSuchElementException("Изображение не прогрузилось после 3 попыток: " + linkId);
    }
}
