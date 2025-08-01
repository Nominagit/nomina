import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class GetDataTest extends BaseTest {

    @Test
    @Disabled
    public void getFieldNames() {
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
    public void getDataFromWipo() throws IOException, InterruptedException {
        String prefix = "18648";
        for (int i = 10; i <= 12; i++) {
            String suffix = String.format("%02d", i);
            String fullNumber = prefix + suffix;

            companyCatalogPage = new CompanyCatalogPage(driver);
            // метод getCompanyData получает список данных на странице по искомому значению
            allData = companyCatalogPage.getCompanyData(fullNumber, "text", wipoLink);

            Thread.sleep(2000);

            // отбираем из имеющихся данных только нужные разделы
            List<WebElement> sliceAllData = allData.subList(7, 17);

            // wipoKeys - наименование переменных в будущем json файле (пока без полей  registrationDate и expirationDate)
            List<String> keys = companyCatalogPage.wipoKeys.subList(0, companyCatalogPage.wipoKeys.size() - 2);
            // сопоставляем наименование переменных с данными из страницы
            Map<String, String> dataMap = companyCatalogPage.getDataMap(keys, sliceAllData);

            try {
                // получаем информацию о переменных registrationDate и expirationDate
                List<WebElement> date = driver.findElements(By.className("date"));

                String regDate = date.get(2).getText();   // registrationDate
                String expDate = date.get(3).getText();   // expirationDate
                // записываем отдельно в json данные по дате
                dataMap.put("registrationDate", regDate);
                dataMap.put("expirationDate", expDate);

                // получаем номер компании
                String appNum = companyCatalogPage.getApplicationNumber_(allData);
                // сохраняем полученные данные в json
                companyCatalogPage.saveDataToJson(appNum, "wipo", dataMap);

                // сохраняем из страницы изображение компании
                companyCatalogPage.downloadImage(wipoLink, "wipo", fullNumber);
            } catch (NoSuchElementException noSuchElementException) {
                companyCatalogPage.downloadImageWithRetry(wipoLink, "wipo", fullNumber);
            }
        }
    }

    @Test
    public void getDataFromAipa() throws IOException, InterruptedException {
        String prefix = "2024";
        for (int i = 1; i <= 5; i++) {
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
                } catch (
                        NoSuchElementException noSuchElementException) { // обработка ошибки на случай, когда картинка не успела загрузиться
                    companyCatalogPage.downloadImageWithRetry(aipaLink, "aipa", fullNumber);
                }
            } else if (allDataCount == 1) { // кейс, когда информация о компании не загружена на сайт
                companyCatalogPage.saveDataToJson_(fullNumber, "aipa", dataMap);
            }
        }
    }
}
