import model.DataModel;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import repository.AipaRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Базовая идея:
 * - AIPA: перебор по префиксам; суффикс 0001..9999; бакет = префикс.
 * - WIPO: перебор по числовому диапазону; бакет = id/10000 (100, 101, ...).
 * - «Нет данных» → сохраняем в error, изображение → плейсхолдер .missing, продолжаем.
 * - Много подряд пропусков для AIPA → переключаем префикс.
 */
public class GetDataTest extends BaseTest {

    private CompanyCatalogPage page;
    private final AipaRepository repository = new AipaRepository("jdbc:postgresql://localhost:5432/postgres", "postgres", "nominapass");

    // --- AIPA ---
    private static final String[] AIPA_PREFIXES = {

            "", "2000", "2001", "2002", "2003", "2004", "2005", "2006", "2007", "2008", "2009", "2010",
            "2011", "2012", "2013", "2014", "2015", "2016", "2017", "2018", "2019", "2020",
            "2021", "2022", "2023", "2024", "2025"
    };

    private static final int AIPA_SUFFIX_START = 1;      // 0001
    private static final int AIPA_SUFFIX_END = 9999;    // пример диапазона; можно 9999
    private static final int AIPA_MAX_MISSES_TO_SKIP_PREFIX = 5; // если подряд столько «пустых» — префикс считаем исчерпанным

    // --- WIPO ---
    // Формируем полное число и идём по диапазону.
    private static final long WIPO_START_ID = 1_170_001L;   // 1000000
    private static final long WIPO_END_ID = 1_170_500L;   // подставь нужный верхний диапазон

    public GetDataTest() throws SQLException {
    }

    @Test
    public void scrapeWipo() throws IOException, InterruptedException {
        page = new CompanyCatalogPage(driver);

        for (long id = WIPO_START_ID; id <= WIPO_END_ID; id++) {
            String fullNumber = String.valueOf(id);
            String bucket = page.wipoBucket(fullNumber);
            var dataModel = new DataModel();

            // Загружаем блоки и пытаемся собрать данные
            List<WebElement> allData = page.getCompanyData(fullNumber, "text", CompanyCatalogPage.WIPO_BASE);

            // 1) Заголовок
            String markName = "";
            try {
                WebElement h = driver.findElement(By.className("markname"));
                String header = (h.getText() == null) ? "" : h.getText().trim();
                int dash = header.indexOf('-');
                markName = (dash > 0) ? header.substring(dash + 1).split("\\R", 2)[0].trim() : header;
            } catch (Exception ignored) {
            }

            // 2) Определяем holder и niceClasses
            String holder = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@class='lapin client holType']"))).getText();
            String niceClasses = new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.presenceOfElementLocated(By.xpath("//td[@class='nice']//div"))).getText();

            // 3) Даты
            String regDate = "", expDate = "";
            Optional<String[]> datesOpt = page.tryReadWipoDates();
            if (datesOpt.isPresent()) {
                regDate = datesOpt.get()[0];
                expDate = datesOpt.get()[1];
            }

            // 4) 732 и 511
            String holder732 = "";
            String goods511 = "";
            try {
                WebElement n732 = driver.findElement(By.xpath(
                        "//div[@class='inidCode' and contains(text(),'732')]/following::div[contains(@class,'text')][1]"
                ));
                holder732 = Optional.ofNullable(n732.getText()).orElse("").trim();
            } catch (NoSuchElementException ignored) {
            }

            try {
                WebElement n511 = driver.findElement(By.xpath(
                        "//div[@class='inidCode' and contains(text(),'511')]/following::div[contains(@class,'text')][1]"
                ));
                goods511 = Optional.ofNullable(n511.getText()).orElse("").trim();
            } catch (NoSuchElementException ignored) {
            }

            // 5) Собираем JSON
            Map<String, String> dataMap = new LinkedHashMap<>();
            dataMap.put("markName", markName);
            dataMap.put("applicationNumber", fullNumber);
            dataMap.put("holder", holder);
            dataMap.put("niceClasses", niceClasses);
            dataMap.put("holder732", holder732);
            dataMap.put("goodsAndServices511", goods511);
            dataMap.put("registrationDate", regDate);
            dataMap.put("expirationDate", expDate);

            boolean hasData = page.hasLikelyValidData(allData);
            String appNum = fullNumber;

            if (hasData) {
                page.saveJson("wipo", "success", bucket, appNum, dataMap);
                dataModel.setType("wipo");
                dataModel.setData(dataMap);
                dataModel.setMarkName(markName);
                dataModel.setFullId(fullNumber);
                dataModel.setLink(CompanyCatalogPage.WIPO_BASE + fullNumber);

                try {
                    page.downloadImage(CompanyCatalogPage.WIPO_BASE, "wipo", bucket, fullNumber, dataModel);
                } catch (IOException | NoSuchElementException e) {
                    page.downloadImageWithRetry(CompanyCatalogPage.WIPO_BASE, "wipo", bucket, fullNumber, dataModel);
                }
                this.repository.addToDatabase(dataModel);
            } else {
                page.saveJson("wipo", "error", bucket, appNum, dataMap);
                page.downloadImageWithRetry(CompanyCatalogPage.WIPO_BASE, "wipo", bucket, fullNumber, dataModel);
            }
        }
    }

    @Test
    public void scrapeAipa() throws Exception {
        page = new CompanyCatalogPage(driver);

        for (String prefix : AIPA_PREFIXES) {
            int consecutiveMisses = 0;

            for (int i = AIPA_SUFFIX_START; i <= AIPA_SUFFIX_END; i++) {
                String suffix = String.format("%04d", i);
                String fullId = prefix + suffix;
                String bucket = page.aipaBucket(prefix); // бакет = префикс
                var dataModel = new DataModel();

                // Загружаем блоки и пытаемся собрать карту
                List<WebElement> allData = page.getCompanyData(fullId, "data", CompanyCatalogPage.AIPA_BASE);

                // Прямой маппинг по ключам AIPA
                Map<String, String> dataMap = page.zipToMap(page.aipaKeys, allData);
                boolean hasData = page.hasLikelyValidData(allData);

                if (hasData) {
                    String appNum = page.extractAipaApplicationNumber(dataMap);
                    String markName = dataMap.getOrDefault("markName", "UNKNOWN");
                    dataModel.setFullId(fullId);
                    dataModel.setMarkName(markName);
                    dataModel.setData(dataMap);
                    dataModel.setLink(CompanyCatalogPage.AIPA_BASE + fullId);
                    dataModel.setType("aipa");

                    page.saveJson("dataModel", "success", bucket, appNum, dataMap);
                    consecutiveMisses = 0;

                    try {
                        page.downloadImage(CompanyCatalogPage.AIPA_BASE, "aipa", bucket, fullId, dataModel);
                    } catch (IOException | NoSuchElementException e) {
                        page.downloadImageWithRetry(CompanyCatalogPage.AIPA_BASE, "aipa", bucket, fullId, dataModel);
                    }
                    this.repository.addToDatabase(dataModel);
                } else {
                    // «Нет данных» — пишем в error с именем по самому id (чтобы отличать)
                    page.saveJson("aipa", "error", bucket, fullId, dataMap);
                    page.downloadImageWithRetry(CompanyCatalogPage.AIPA_BASE, "aipa", bucket, fullId, dataModel);

                    consecutiveMisses++;
                    // Если в начале префикса или в целом подряд слишком много пустых — переключаемся на следующий префикс
                    if (consecutiveMisses >= AIPA_MAX_MISSES_TO_SKIP_PREFIX) {
                        System.out.println("Prefix " + prefix + " looks exhausted. Skipping rest.");
                        break;
                    }
                }
            }
        }
    }
}
