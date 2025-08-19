import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.util.*;

/**
 * Базовая идея:
 *  - AIPA: перебор по префиксам; суффикс 0001..9999; бакет = префикс.
 *  - WIPO: перебор по числовому диапазону; бакет = id/10000 (100, 101, ...).
 *  - «Нет данных» → сохраняем в error, изображение → плейсхолдер .missing, продолжаем.
 *  - Много подряд пропусков для AIPA → переключаем префикс.
 */
public class GetDataTest extends BaseTest {

    private CompanyCatalogPage page;

    // --- AIPA ---
    private static final String[] AIPA_PREFIXES = {
            "", "2000"
          /* , "2001", "2002", "2003", "2004", "2005", "2006", "2007", "2008", "2009", "2010",
            "2011", "2012", "2013", "2014", "2015", "2016", "2017", "2018", "2019", "2020",
            "2021", "2022", "2023", "2024", "2025" */
            };

    private static final int AIPA_SUFFIX_START = 1;      // 0001
    private static final int AIPA_SUFFIX_END   = 10;    // пример диапазона; можно 9999
    private static final int AIPA_MAX_MISSES_TO_SKIP_PREFIX = 5; // если подряд столько «пустых» — префикс считаем исчерпанным

    // --- WIPO ---
    // Формируем полное число и идём по диапазону.
    private static final long WIPO_START_ID = 1_000_000L;   // 1000000
    private static final long WIPO_END_ID   = 1_000_020L;   // подставь нужный верхний диапазон

    @Test
    public void scrapeWipo() throws IOException, InterruptedException {
        page = new CompanyCatalogPage(driver);

        for (long id = WIPO_START_ID; id <= WIPO_END_ID; id++) {
            String fullNumber = String.valueOf(id);
            String bucket = page.wipoBucket(fullNumber); // бакет = префикс

            // Загружаем блоки и пытаемся собрать данные
            List<WebElement> allData = page.getCompanyData(fullNumber, "text", CompanyCatalogPage.WIPO_BASE);

            // Используем первые 9 текстовых блоков как «основные»
            // (holder...madridDesignation), а даты читаем отдельно
            List<String> keys = page.wipoKeys.subList(0, page.wipoKeys.size() - 2);
            List<WebElement> mainBlocks = allData.size() >= keys.size() ? allData.subList(0, keys.size()) : allData;

            Map<String, String> dataMap = page.zipToMap(keys, mainBlocks);

            // Даты (registration/expiration) — чтение
            page.tryReadWipoDates().ifPresent(dates -> {
                dataMap.put("registrationDate", dates[0]);
                dataMap.put("expirationDate", dates[1]);
            });

            boolean hasData = page.hasLikelyValidData(allData);

            // Имя файла: корректный application number, иначе id
            String appNum = page.extractWipoApplicationNumber(allData, fullNumber);

            if (hasData) {
                page.saveJson("wipo", "success", bucket, appNum, dataMap);
                try {
                    page.downloadImage(CompanyCatalogPage.WIPO_BASE, "wipo", bucket, fullNumber);
                } catch (IOException | NoSuchElementException e) {
                    // ретраи и плейсхолдер
                    page.downloadImageWithRetry(CompanyCatalogPage.WIPO_BASE, "wipo", bucket, fullNumber);
                }
            } else {
                // «Нет данных» или разметка не та
                page.saveJson("wipo", "error", bucket, appNum, dataMap);
                page.downloadImageWithRetry(CompanyCatalogPage.WIPO_BASE, "wipo", bucket, fullNumber);
            }
        }
    }

    @Test
    public void scrapeAipa() throws IOException, InterruptedException {
        page = new CompanyCatalogPage(driver);

        for (String prefix : AIPA_PREFIXES) {
            int consecutiveMisses = 0;

            for (int i = AIPA_SUFFIX_START; i <= AIPA_SUFFIX_END; i++) {
                String suffix = String.format("%04d", i);
                String fullId = prefix + suffix;
                String bucket = page.aipaBucket(prefix); // бакет = префикс

                // Загружаем блоки и пытаемся собрать карту
                List<WebElement> allData = page.getCompanyData(fullId, "data", CompanyCatalogPage.AIPA_BASE);

                // Прямой маппинг по ключам AIPA
                Map<String, String> dataMap = page.zipToMap(page.aipaKeys, allData);
                boolean hasData = page.hasLikelyValidData(allData);

                if (hasData) {
                    String appNum = page.extractAipaApplicationNumber(dataMap);
                    page.saveJson("aipa", "success", bucket, appNum, dataMap);
                    consecutiveMisses = 0;

                    try {
                        page.downloadImage(CompanyCatalogPage.AIPA_BASE, "aipa", bucket, fullId);
                    } catch (IOException | NoSuchElementException e) {
                        page.downloadImageWithRetry(CompanyCatalogPage.AIPA_BASE, "aipa", bucket, fullId);
                    }
                } else {
                    // «Нет данных» — пишем в error с именем по самому id (чтобы отличать)
                    page.saveJson("aipa", "error", bucket, fullId, dataMap);
                    page.downloadImageWithRetry(CompanyCatalogPage.AIPA_BASE, "aipa", bucket, fullId);

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
