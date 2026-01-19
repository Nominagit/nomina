import model.DataModel;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import repository.AipaRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private static final String[] AIPA_PREFIXES = {
            "2026"
    };

    private static final int AIPA_SUFFIX_START = 1;      // 0001
    private static final int AIPA_SUFFIX_END = 9999;    // пример диапазона; можно 9999
    private static final int AIPA_MAX_MISSES_TO_SKIP_PREFIX = 130; // если подряд столько «пустых» — префикс считаем исчерпанным

    // --- WIPO ---
    private static final int WIPO_START_ID = 1;   // 1000000
    private static final int WIPO_END_ID = 51894;   // подставь нужный верхний диапазон - 1882100

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
        page.wipoSortByRegDate();       // выбор сортировки по дате регистрации

        // Получение данных по дате последней регистрации компаний и проверка на актуальность даты
//        LocalDate lastRegDate = page.wipoGetRegDate();  // получение даты последней зарегистрировавшейся компании
//        LocalDate stopDate = LocalDate.of(2025, 10, 31); // <-- нужная дата (порог)
//        if (!lastRegDate.isAfter(stopDate)) { // lastRegDate <= stopDate
//            return;
//        }

        page.wipoSelectPageValue("156"); // выбор номера страницы отображаемых элементов
        Thread.sleep(3000);
        page.wipoSelectCompanyFromTheList("66"); // выбор номера элемента из списка

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(60));

        Thread.sleep(2000);
        By nextBtnLoc = By.id("topDocNext");
        Thread.sleep(2000);
        By markNameLoc = By.cssSelector(".markname"); // используем в хелпере

        for (int id = WIPO_START_ID; id <= WIPO_END_ID; id++) {
            var dataModel = new DataModel();

            // Загружаем блоки и пытаемся собрать данные
            List<WebElement> allData = page.getCompanyData("text");

            // Номер обращения
            String fullNumber = "";
            WebElement h = page.findInAllFrames(driver, markNameLoc, Duration.ofSeconds(20)); // <<< замена
            String t = (h.getText() == null ? "" : h.getText().trim())
                    .replace('\u2013', '-')  // – en dash
                    .replace('\u2014', '-'); // — em dash

            Matcher m = Pattern.compile("^\\s*(\\d+)\\s*[-—–]?").matcher(t);
            fullNumber = m.find() ? m.group(1) : "";

            // Определяем корзину - bucket
            String bucket = page.wipoBucket(fullNumber);

            // Заголовок
            String markName = "";
            try {
                WebElement h2 = page.findInAllFrames(driver, markNameLoc, Duration.ofSeconds(20));
                String header = (h2.getText() == null) ? "" : h2.getText().trim()
                        .replace('\u2013', '-')  // –
                        .replace('\u2014', '-'); // —

                int dash = header.indexOf('-');

                if (dash > 0) {
                    // Ситуация: "1896258 - MULTIBURN"
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

            /* ========== Закомментировать участок кода при первичном сборе данных ========== */
            // сравнение (типы regDate/expDate не трогаем)
//            if (regDate != null && !regDate.isBlank()) {
//                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
//                LocalDate regLocalDate = LocalDate.parse(regDate.trim(), fmt);
//
//                if (regLocalDate.isEqual(stopDate)) {
//                    System.out.println("\nINFO: Все актуальные данные получены (достигнут порог stopDate: " + stopDate + "). \nСбор данных остановлен.");
//                    break;
//                }
//            }

            // Определяем holder и niceClasses
            String holder = "";
            String niceClasses = "";
            try {
                holder = new WebDriverWait(driver, Duration.ofSeconds(60))
                        .until(ExpectedConditions.presenceOfElementLocated(By.xpath("(//div[@class='lapin client holType'])[1]"))).getText();
            } catch (TimeoutException e) {
                page.saveJson("wipo", "error", bucket, fullNumber, Map.of("applicationNumber", fullNumber, "reason", "holder-missing"));

                wait.until(ExpectedConditions.refreshed(
                        ExpectedConditions.elementToBeClickable(nextBtnLoc)
                )).click(); // переход на следующую страницу
                continue;
            }

            try {
                niceClasses = new WebDriverWait(driver, Duration.ofSeconds(60))
                        .until(ExpectedConditions.presenceOfElementLocated(By.xpath("//td[@class='nice']//div"))).getText();
            } catch (TimeoutException e) {
                page.saveJson("wipo", "error", bucket, fullNumber, Map.of("applicationNumber", fullNumber, "reason", "niceClasses-missing"));
                wait.until(ExpectedConditions.refreshed(
                        ExpectedConditions.elementToBeClickable(nextBtnLoc)
                )).click(); // переход на следующую страницу
                continue;
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
                    page.downloadImage("wipo", "success".equals("success") ? bucket : bucket, fullNumber, dataModel);
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
