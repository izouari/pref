package org.auto.appointments;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.quartz.SchedulerException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AppointmentTest {


    final static Logger logger = Logger.getLogger(AppointmentTest.class);

    public static String guichet1 = "planning5955";
    public static String guichet2 = "planning5968";
    public static String guichet3 = "planning5973";

    public static String guichetName1 = "GUICHET 21";
    public static String guichetName2 = "GUICHET 22";
    public static String guichetName3 = "GUICHET 24";

    public static String retryAppointment() throws IOException, InterruptedException {

        logger.info("Tentative de prise de rendez-vous demandé par l'utilisateur ");

        return takeAppointment();

    }


    private static void firstStep(WebDriver driver) {
        // 2 - Accepter les conditions
        driver.findElement(By.id("condition")).click();
        driver.findElement(By.name("nextButton")).click();
    }

    private static void secondStep(WebDriver driver, int secondStepRetries , boolean secondStepKO) throws InterruptedException {


            if (secondStepKO && secondStepRetries <3) {
                try{
                    // 3 - Choix du nature de rendez-vous
                    Thread.sleep(1000);
                    driver.findElement(By.id("planning5968")).click();
                    driver.findElement(By.name("nextButton")).click();
                    secondStepRetries++;
                } catch (Exception ex) {

                    logger.warn("Error occured while executing the second step ");
                    secondStepRetries ++;
                    // driver.get(driver.getCurrentUrl());
                    driver.navigate().refresh();
                    logger.info("Retry second step "+ secondStepRetries  + " Current URL : " + driver.getCurrentUrl());
                    secondStep(driver, secondStepRetries, secondStepKO);
                }
                secondStepKO = false;
            }

    }



    private static String excecuteAppointment(WebDriver driver, String guichetName, String guichetId) throws IOException, InterruptedException {
        String responseText;
        try {

           driver.get("http://www.val-de-marne.gouv.fr/booking/create/4963/1");

            secondStep(driver, 0, true, guichetId);

            String response = driver.findElement(By.id("inner_Booking")).getText();
            logger.info(response);

            if (response.contains("existe plus de plage horaire libre")) {
                logger.info("["+ guichetName +"] - " + driver.findElement(By.id("FormBookingCreate")).getText());

                responseText = "["+ guichetName +"] - " + driver.findElement(By.id("FormBookingCreate")).getText();

            } else {

                logger.info("********* RENDEZ-VOUS DISPO AU " + guichetName );
                logger.info(driver.findElement(By.id("inner_Booking")).getText());
                responseText = "!!!! RENDEZ-VOUS DISPO AU " + guichetName + "  !!!! :D ";
            }

        } catch (NoSuchElementException | InterruptedException ex ) {
            logger.warn("Error occured while booking appointment : " + ex.getMessage());
            responseText = "["+ guichetName +"] - Tentative échouée :( réessayer encore .. ";

        }

        TelegramMessageSender.sendMessage(responseText);

        return responseText;
    }


    private static void secondStep(WebDriver driver, int secondStepRetries , boolean secondStepKO, String guichet) throws InterruptedException {

        if (secondStepKO && secondStepRetries <3) {
            try{
                // 3 - Choix du nature de rendez-vous
                driver.findElement(By.id(guichet)).click();
                driver.findElement(By.name("nextButton")).click();
                secondStepRetries++;
            } catch (Exception ex) {

                // logger.warn("Error occured while executing the second step ");
                secondStepRetries ++;
                // driver.get(driver.getCurrentUrl());
                driver.navigate().refresh();

                // logger.info("Retry second step "+ secondStepRetries  + " Current URL : " + driver.getCurrentUrl());

                Thread.sleep(2000);

                secondStep(driver, secondStepRetries, secondStepKO,guichet);
            }
            secondStepKO = false;
        }

    }


    private static String lookForAppointment() throws InterruptedException {
        WebDriver driver = new HtmlUnitDriver(BrowserVersion.FIREFOX_68, true);
        // 1 | open | /booking/create/4963/1 |


        int trials = 0;
        String siteTitle = null;


        while (trials < 5 && (siteTitle == null || siteTitle.isEmpty())) {
            driver.get("http://www.val-de-marne.gouv.fr/booking/create/4963/0");
            try {
                if (!driver.getTitle().isEmpty()) {
                    siteTitle = driver.getTitle();
                    // logger.info(siteTitle);
                } else {
                    Thread.sleep(4000);
                    logger.warn("Unreachable site !  , retrying access " + (trials + 1));
                }
            } catch (Exception ex) {
                Thread.sleep(2000);
                logger.warn("Unreachable site , retrying access " + (trials + 1));
            }
            trials++;
        }

        if (driver.getTitle().isEmpty()) {
            driver.get("http://www.val-de-marne.gouv.fr/booking/create/4963/1");
            // siteTitle = driver.getTitle();
            // logger.info("Retrying without first step : " + siteTitle);
        } else {
            logger.info("Executing first step ");
            firstStep(driver);
        }
        logger.info("Executing second step ");

        secondStep(driver, 0, true);

        if (driver.findElement(By.id("inner_Booking")).getText().contains("existe plus de plage horaire libre")) {
            return driver.findElement(By.id("FormBookingCreate")).getText();

        } else {
            logger.info(driver.findElement(By.id("inner_Booking")).getText());
            return "YEEES :D  RENDEZ-VOUS DISPO , FAIT VITE !! ";

        }
    }


    private static String takeAppointment() throws IOException, InterruptedException {

        final WebClient webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setRedirectEnabled(true);

        HtmlPage page = null;
        int trials = 0;

        while (trials < 5 && page == null) {

            try {
                page  = webClient.getPage("http://www.val-de-marne.gouv.fr/booking/create/4963/1");

            } catch (FailingHttpStatusCodeException ex) {
                logger.warn( ex.getLocalizedMessage() + " Retry access for the " + (trials + 1) + " time");
                Thread.sleep(8000);
            }
            trials++;
        }

        if (page == null ) return "Le site est actuellement injoinable :( ";

        HtmlPage pageGuichet1 = SerializationUtils.clone(page);
        HtmlPage pageGuichet2 = SerializationUtils.clone(page);
        HtmlPage pageGuichet3 = SerializationUtils.clone(page);

        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Callable, return a future, submit and run the task async
        Future<String> futureTask1 = executor.submit(() -> takeAppointmentByGuichet(pageGuichet1, guichet1));

        Thread.sleep(10000);

        Future<String> futureTask2 = executor.submit(() -> takeAppointmentByGuichet(pageGuichet2, guichet2));

        Thread.sleep(10000);

        Future<String> futureTask3 = executor.submit(() -> takeAppointmentByGuichet(pageGuichet3, guichet3));

        String guichet1Result = null;
        String guichet2Result = null;
        String guichet3Result = null;

        try {
            guichet1Result = futureTask1.get();
            guichet2Result = futureTask2.get();
            guichet3Result = futureTask3.get();

        } catch (InterruptedException | ExecutionException e) {// thread was interrupted
            e.printStackTrace();
        } finally {
            // shut down the executor manually
            executor.shutdown();
        }

        return "["+ guichetName1 +"] : " + guichet1Result + "\n" +
                "["+ guichetName2 +"] : " + guichet2Result + "\n" +
                "["+ guichetName3 +"] : " + guichet3Result + "\n Lien du rendez-vous: http://www.val-de-marne.gouv.fr/booking/create/4963/1" ;

    }


    private static String takeAppointmentByGuichet(HtmlPage page , String guichet) throws InterruptedException, IOException {

        // logger.info("Prise dez rendez-vous en cours au guichet " + guichet);

        final DomElement guichetRadio = page.getElementById(guichet);

        final DomElement nextButton = page.getElementByName("nextButton");

        int trials = 0;

        boolean isReachable = false;

        while (trials < 5 && !isReachable) {
            try {
                guichetRadio.click();
                page = nextButton.click();

                isReachable = true;
            } catch (FailingHttpStatusCodeException ex) {
                logger.warn(guichet + " : " + ex.getLocalizedMessage() + " . Retry " + trials);
                Thread.sleep(3000);
            }
            trials ++;
        }

        if (trials >= 5) return "Non accessible pour l'instant";

        final DomElement takenMsg = page.getElementById("inner_Booking");


        if (takenMsg.getTextContent().contains("existe plus de plage horaire libre")) {

            final DomElement responseMsg = page.getElementById("FormBookingCreate");
            logger.info( guichet + " : " + responseMsg.getTextContent().trim());
            return responseMsg.getTextContent().trim();

        } else if (takenMsg.getTextContent().contains("Description de la nature")) {

            logger.info("Reponse : " + takenMsg.getTextContent());
            return "YEEES :D  RENDEZ-VOUS DISPO DANS CE GUICHET  ";

        } else {
            logger.info("Reponse : " + takenMsg.getTextContent());
            return "Réponse non reconnue. Requete redirigée vers la page d'accueil";
        }
    }


}
