import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
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

    public List<WebElement> getCompanyData(String linkId, String searchingData, String dataLink) throws InterruptedException {
        driver.get(dataLink + linkId);
        Thread.sleep(2000);
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
    // Возвращает null, если элемент не найден
    public String getCompanyImageSrc(String dataLink, String linkId) {
        driver.get(dataLink + linkId);

        // Явное ожидание небольшой паузы, если страница долго рендерится (необязательно)
        new WebDriverWait(driver, Duration.ofSeconds(3))
                .until(d -> ((JavascriptExecutor) d)
                        .executeScript("return document.readyState")
                        .equals("complete"));

        // используем findElements, чтобы не получать исключение
        List<WebElement> imgs = driver.findElements(
                By.xpath("//img[contains(@src, '" + linkId + "')]")
        );
        if (imgs.isEmpty()) {
            // картинка не найдена
            return null;
        }
        return imgs.get(0).getAttribute("src");
    }

    // Скачивает картинку или создаёт файл-заглушку со "*" при отсутствии
    public void downloadImage(String dataLink, String dataName, String linkId) throws IOException {
        String imageUrl = getCompanyImageSrc(dataLink, linkId);
        File realPath = generatePathToImage(dataName, linkId);
        File parent = realPath.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (imageUrl == null) {
            // создаём файл-заглушку с * в имени
            File placeholder = new File(parent, "*" + linkId + ".jpg");
            if (!placeholder.exists()) {
                placeholder.createNewFile();
                System.out.println(">> Placeholder file created: " + placeholder.getAbsolutePath());
            }
            return;
        }
        // обычная загрузка картинки в realPath
        URL base = new URL(dataLink);
        URL imgUrl = new URL(base, imageUrl);
        BufferedImage img = ImageIO.read(imgUrl);
        if (img == null) {
            throw new IOException("ImageIO.read вернул null для " + imgUrl);
        }
        ImageIO.write(img, "jpg", realPath);
    }

    /**
     * То же самое, но с тремя попытками. Если всё три раза упало —
     * единоразово пишем '*'.
     */
    public void downloadImageWithRetry(String dataLink, String dataName, String linkId) throws IOException {
        int attempts = 0;
        while (attempts < 3) {
            try {
                downloadImage(dataLink, dataName, linkId);
                return;
            } catch (IOException e) {
                attempts++;
                System.err.println("Попытка " + attempts + " неудачна: " + e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
        // после трёх неудач — ставим тоже placeholder
        File realPath = generatePathToImage(dataName, linkId);
        File parent = realPath.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        File placeholder = new File(parent, "*" + linkId + ".jpg");
        if (!placeholder.exists()) {
            placeholder.createNewFile();
            System.out.println(">> After retries: placeholder created " + placeholder.getAbsolutePath());
        }
    }
}
