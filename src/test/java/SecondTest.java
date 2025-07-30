
import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoSuchElementException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class SecondTest extends BaseTest {

    @Test
    public void test_image_001() throws IOException {
        companyCatalogPage = new CompanyCatalogPage(driver);
        companyCatalogPage.downloadImage(aipaLink, "aipa", "20242122");
    }

    @Test
    public void test_003() throws IOException, InterruptedException {
        String prefix = "2024";
        for (int i = 1; i <= 100; i++) {
            // TODO - продумай логику на изменение нумерации согласно требуемой логики
            String suffix = String.format("%04d", i);
            String fullNumber = prefix + suffix;

            companyCatalogPage = new CompanyCatalogPage(driver);
            // метод getCompanyData получает список данных на странице по искомому значению
            allData = companyCatalogPage.getCompanyData(fullNumber, "data", aipaLink);

            Thread.sleep(2000);

            // считаем количество искомых заголовков элементов на странице
            // будем использовать далее в if
            int allDataCount = allData.size();

            // aipaKeys - наименование переменных в будущем json файле
            List<String> keys = companyCatalogPage.aipaKeys;
            // сопоставляем наименование переменных с данными из страницы
            Map<String, String> dataMap = companyCatalogPage.getDataMap(keys, allData);

            // кейс, когда страница содержит все нужные нам заглоовки и данные о компании
            if (allDataCount > 1) {
                try {
                    // получаем номер компании
                    String appNum = companyCatalogPage.getApplicationNumber(dataMap);
                    // сохраняем полученные данные в json
                    companyCatalogPage.saveDataToJson(appNum, "aipa", dataMap);
                    // сохраняем из страницы изображение компании
                    companyCatalogPage.downloadImage(aipaLink, "aipa", fullNumber);
                } catch (NoSuchElementException noSuchElementException) { // обработка ошибки на случай, когда картинка не успела загрузиться
                    companyCatalogPage.downloadImageWithRetry(aipaLink, "aipa", fullNumber);
                }
            } else if (allDataCount == 1) { // кейс, когда информация о компании не загружена на сайт
                companyCatalogPage.saveDataToJson_(fullNumber, "aipa", dataMap);
            }
        }
    }
}
