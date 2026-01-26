import model.DataModel;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Базовая идея:
 * - AIPA: перебор по префиксам; суффикс 0001..9999; бакет = префикс.
 * - WIPO: перебор по числовому диапазону; бакет = id/10000 (100, 101, ...).
 * - «Нет данных» → сохраняем в error, изображение → плейсхолдер .missing, продолжаем.
 * - Много подряд пропусков для AIPA → переключаем префикс.
 */
public class UpdateDataTest extends BaseTest {

    private CompanyCatalogPage page;
    private final AipaRepository repository = new AipaRepository("jdbc:postgresql://localhost:5432/postgres", "postgres", "nominapass");

    // --- AIPA ---
    private static final String[] AIPA_PREFIXES = {"2026"};
    private static final int AIPA_SUFFIX_START = 1;      // 0001
    private static final int AIPA_SUFFIX_END = 9999;     // 9999
    private static final int AIPA_MAX_MISSES_TO_SKIP_PREFIX = 1500; // если подряд столько «пустых» — префикс считаем исчерпанным

    // --- WIPO ---
    // теперь значение берём из файла при старте (если файла нет — используем дефолт)
    private static String WIPO_LAST_ACTUAL_NUMBER = WipoStateStore.loadOrDefault("1896539");
    private static final int WIPO_START_ID = 1;
    private static final int WIPO_END_ID = 51901;

    public UpdateDataTest() throws SQLException {
    }

    @Test
    public void scrapeWipoARM() throws IOException, InterruptedException {
        page = new CompanyCatalogPage(driver);
        driver.get(CompanyCatalogPage.WIPO_BASE_UPDATE);

        /**
         * Работа с фильтрами перед началом сбора данных
         */
        page.wipoSelectDisplayValue(); // выбор количества отображаемых элементов на странице
        Thread.sleep(2000);
        page.wipoSortByRegDate(); // выбор сортировки по дате регистрации
        Thread.sleep(2000);

        // Получение данных по актуальному номеру Reg.No
        List<WebElement> companyId = driver.findElements(By.xpath(
                "//table[@id='gridForsearch_pane']//tr[contains(@class,'jqgrow')]/td[starts-with(@title,'ROM.')]"));
        String titleValue = companyId.get(0).getAttribute("title");
        String regNo = titleValue.replace("ROM.", "").trim();

        page.wipoSelectPageValue("1");
        Thread.sleep(3000);
        page.wipoSelectCompanyFromTheList("0");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        Thread.sleep(2000);
        By nextBtnLoc = By.id("topDocNext");
        Thread.sleep(2000);
        By markNameLoc = By.cssSelector(".markname");

        for (int id = WIPO_START_ID; id <= WIPO_END_ID; id++) {
            var dataModel = new DataModel();

            // Загружаем блоки и пытаемся собрать данные
            List<WebElement> allData = page.getCompanyData("text");

            // Номер обращения
            String fullNumber = "";
            WebElement h = page.findInAllFrames(driver, markNameLoc, Duration.ofSeconds(20));
            String t = (h.getText() == null ? "" : h.getText().trim())
                    .replace('\u2013', '-')  // – en dash
                    .replace('\u2014', '-'); // — em dash

            Matcher m = Pattern.compile("^\\s*(\\d+)\\s*[-—–]?").matcher(t);
            fullNumber = m.find() ? m.group(1) : "";

            // сравнение Reg.No с последним актуальным - WIPO_LAST_ACTUAL_NUMBER
            if (fullNumber != null && !fullNumber.isBlank()) {
                if (fullNumber.equals(WIPO_LAST_ACTUAL_NUMBER)) {
                    System.out.println("\nINFO: Все актуальные данные получены. \nСбор данных остановлен.");

                    // обновляем значение и сохраняем в файл (чтобы запомнилось на следующий запуск)
                    WIPO_LAST_ACTUAL_NUMBER = regNo;
                    WipoStateStore.save(WIPO_LAST_ACTUAL_NUMBER);
                    break;
                }
            }

            // Определяем корзину - bucket
            String bucket = page.wipoBucket(fullNumber);

            // Заголовок
            String markName = "";
            try {
                WebElement h2 = page.findInAllFrames(driver, markNameLoc, Duration.ofSeconds(20));
                String header = (h2.getText() == null) ? "" : h2.getText().trim()
                        .replace('\u2013', '-')
                        .replace('\u2014', '-');

                int dash = header.indexOf('-');

                if (dash > 0) {
                    String candidate = header.substring(dash + 1).split("\\R", 2)[0].trim();
                    if (!candidate.isEmpty()) {
                        markName = candidate;
                    }
                }

                // fallback: если в заголовке имени нет — читаем INID 561
                if (markName.isEmpty()) {
                    try {
                        WebElement n561 = driver.findElement(By.xpath(
                                "//div[@class='inidCode' and contains(text(),'561')]/following::div[contains(@class,'text')][1]"
                        ));
                        markName = Optional.ofNullable(n561.getText()).orElse("").trim();
                    } catch (NoSuchElementException ignored) {
                    }
                }

            } catch (Exception ignored) {
            }

            // Даты
            String regDate = "", expDate = "";
            Optional<String[]> datesOpt = page.tryReadWipoDates();
            if (datesOpt.isPresent()) {
                regDate = datesOpt.get()[0];
                expDate = datesOpt.get()[1];
            }

            // Определяем holder и niceClasses
            String holder = "";
            String niceClasses = "";
            try {
                holder = new WebDriverWait(driver, Duration.ofSeconds(60))
                        .until(ExpectedConditions.presenceOfElementLocated(
                                By.xpath("(//div[@class='lapin client holType'])[1]")
                        )).getText();
            } catch (TimeoutException e) {
                page.saveJson("wipo", "error", bucket, fullNumber,
                        Map.of("applicationNumber", fullNumber, "reason", "holder-missing"));

                wait.until(ExpectedConditions.refreshed(
                        ExpectedConditions.elementToBeClickable(nextBtnLoc)
                )).click(); // переход на следующую страницу
                continue;
            }

            try {
                niceClasses = new WebDriverWait(driver, Duration.ofSeconds(60))
                        .until(ExpectedConditions.presenceOfElementLocated(
                                By.xpath("//td[@class='nice']//div")
                        )).getText();
            } catch (TimeoutException e) {
                page.saveJson("wipo", "error", bucket, fullNumber,
                        Map.of("applicationNumber", fullNumber, "reason", "niceClasses-missing"));

                wait.until(ExpectedConditions.refreshed(
                        ExpectedConditions.elementToBeClickable(nextBtnLoc)
                )).click(); // переход на следующую страницу
                continue;
            }

            // 732 и 511
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

            // Собираем JSON
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
                    page.downloadImage("wipo", bucket, fullNumber, dataModel);
                } catch (IOException | NoSuchElementException e) {
                    page.downloadImageWithRetry("wipo", bucket, fullNumber, dataModel);
                }

                this.repository.addToDatabase(dataModel);

                wait.until(ExpectedConditions.refreshed(
                        ExpectedConditions.elementToBeClickable(nextBtnLoc)
                )).click(); // переход на следующую страницу
            } else {
                page.saveJson("wipo", "error", bucket, appNum, dataMap);
                page.downloadImageWithRetry("wipo", bucket, fullNumber, dataModel);

                wait.until(ExpectedConditions.refreshed(
                        ExpectedConditions.elementToBeClickable(nextBtnLoc)
                )).click(); // переход на следующую страницу
            }
        }
    }

    /**
     * Определяет, есть ли "крупная" картинка знака на странице.
     * Используем naturalWidth/naturalHeight, чтобы отсечь мелкие иконки.
     */
    private boolean hasAipaMarkImageOnPage() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            List<WebElement> imgs = driver.findElements(By.tagName("img"));

            for (WebElement img : imgs) {
                if (img == null) continue;
                if (!img.isDisplayed()) continue;

                String src = img.getAttribute("src");
                if (src == null || src.isBlank()) continue;

                long w = ((Number) js.executeScript("return arguments[0].naturalWidth || 0;", img)).longValue();
                long h = ((Number) js.executeScript("return arguments[0].naturalHeight || 0;", img)).longValue();

                // порог можно подкрутить, но для "знака" обычно подходит
                if (w >= 120 && h >= 120) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }


    @Test
    public void scrapeAipa() throws Exception {
        page = new CompanyCatalogPage(driver);

        for (String prefix : AIPA_PREFIXES) {
            int consecutiveMisses = 0;

            int startSuffix = Math.max(AIPA_SUFFIX_START, AipaStateStore.loadOrDefault(prefix, AIPA_SUFFIX_START));

            for (int i = startSuffix; i <= AIPA_SUFFIX_END; i++)  {
                String suffix = String.format("%04d", i);
                String fullId = prefix + suffix;
                String bucket = page.aipaBucket(prefix);
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

                    // кейс 3 = нет текста (мы уже в else) И нет картинки
                    boolean isCase3 = !hasAipaMarkImageOnPage();

                    if (isCase3) {
                        System.out.println("Prefix " + prefix + " looks exhausted. Skipping rest.");
                        // после успешного сохранения (hasData == true)
                        AipaStateStore.save(prefix, i);
                        break;
                    }
                }
            }
        }
    }
}
