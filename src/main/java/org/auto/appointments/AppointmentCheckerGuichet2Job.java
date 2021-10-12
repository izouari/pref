package org.auto.appointments;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.log4j.Logger;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Actions;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

public class AppointmentCheckerGuichet2Job implements Job {

    final static Logger logger = Logger.getLogger(AppointmentCheckerGuichet2Job.class);
    private static String  HOUR_FORMAT = "HH:mm";
    private static String start = "23:55";
    private static String end   = "23:59";
    public static int trialsNumber = 0;
    public static int trialsIndex = 0;
    public static int failedTrials = 0;

    public static String guichet1 = "planning5955";
    public static String guichet2 = "planning5968";

    public static boolean isStoped = false;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        if (isStoped) {
            return;
        }
        if (failedTrials > 2 ) {
            failedTrials--;
            logger.warn("Skip this retry and wait for next retry as site still unreachable ");
            return;
        }

        trialsIndex ++;

        logger.info( "[" + trialsIndex +"] - [" + jobExecutionContext.getJobDetail().getKey() +"] - CHECKING APPOINTMENT ");

        try {
            takeAppointment();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
                final DomElement guichetRadio = page.getElementById(guichet2);
                final DomElement nextButton = page.getElementByName("nextButton");
                Thread.sleep(2000);
                guichetRadio.click();
                page = nextButton.click();

            } catch (FailingHttpStatusCodeException ex) {
                logger.warn( ex.getLocalizedMessage() + " Retry access for the " + (trials + 1) + " time");
                Thread.sleep(8000);
            }
            trials++;
        }

        DomElement takenMsg = page.getElementById("inner_Booking");

        trials = 0;
        while (takenMsg.getTextContent().contains("Choix de la nature du rendez-vous") && trials < 3) {

            logger.warn("Page result doesn't changed ! Retrying another appointment check  " + (trials +1));

            page.getElementById(guichet2).click();
            Thread.sleep(1000);
            page.getElementByName("nextButton").click();
            Thread.sleep(1000);
            takenMsg = page.getElementById("inner_Booking");
            trials ++;
        }

        trialsNumber ++;

        if (failedTrials > 0) {
            failedTrials--;
        }

        if (isNowInInterval(start, end)) {

            trialsNumber = 0;
            trialsIndex = 0;
        }

        if (takenMsg.getTextContent().contains("existe plus de plage horaire libre")) {

            final DomElement responseMsg = page.getElementById("FormBookingCreate");
            logger.info(" PAS DE RENDEZ-VOUS DISPO AU GUICHET 2  " );
            return responseMsg.getTextContent().trim();

        } else {
            logger.info("Reponse : " + takenMsg.getTextContent());
            TelegramMessageSender.sendMessage("YEEES :D  RENDEZ-VOUS DISPO  AU GUICHET 2 ");
            return "YEEES :D  RENDEZ-VOUS DISPO  AU GUICHET 2 ";

        }

    }



    private static void excecuteAppointment(WebDriver driver, String guichet) {
        // 1 | open | /booking/create/4963/1 |

        try {

            int trials = 0;
            String siteTitle = null;

            driver.get("http://www.val-de-marne.gouv.fr/booking/create/4963/0");


            while (trials < 5 && (siteTitle == null || siteTitle.isEmpty())) {
                try {
                    if (!driver.getTitle().isEmpty()) {
                        siteTitle = driver.getTitle();
                        // logger.info(siteTitle);
                    } else {
                        Thread.sleep(5000);
                        logger.warn("Unreachable site ! retrying access " + (trials + 1));
                        driver.navigate().refresh();
                    }
                } catch (Exception ex) {
                    Thread.sleep(5000);
                    logger.warn("Unreachable site ! retrying access " + (trials + 1));
                    driver.navigate().refresh();

                }
                trials++;
            }

            if (driver.getTitle().isEmpty()) {
                driver.get("http://www.val-de-marne.gouv.fr/booking/create/4963/1");
                // logger.info("Retrying without first step : " + siteTitle);
            } else {
                try{
                    firstStep(driver);
                } catch (Exception ex) {
                    logger.warn("Error occured while executing the first step " + ex.getMessage());
                }
            }

            secondStep(driver, 0, true, guichet);


            if (driver.findElement(By.id("inner_Booking")).getText().contains("existe plus de plage horaire libre")) {
                trialsNumber ++;
                logger.info("[" + trialsNumber +"] - PAS DE RENDEZ-VOUS DISPO :( ");
                // logger.info(TelegramMessageSender.sendMessage(" :(((( PAS DE RENDEZ-VOUS DISPO :((( "));

            } else {
                try {

                    logger.info("********* RENDEZ-VOUS DISPO :D ");

                    logger.info(driver.findElement(By.id("inner_Booking")).getText());
                    logger.info(TelegramMessageSender.sendMessage("!!!! RENDEZ-VOUS DISPO !!!! :D "));
                    trialsNumber ++;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        } catch (NoSuchElementException | InterruptedException ex ) {
            logger.warn("Error occured while booking appointment : " + ex.getMessage());
        }

        if (isNowInInterval(start, end)) {
            logger.info(" [Statut du jour] -  Nombre de tentatives effectuées : " + trialsNumber);
            try {
                logger.info(TelegramMessageSender.sendMessage("[Récap du jour] -  Nombre de tentatives effectuées : " + trialsNumber));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            trialsNumber = 0;
            trialsIndex = 0;
            failedTrials = 0;
        }

    }

    private static void firstStep(WebDriver driver) {
        // 2 - Accepter les conditions
        driver.findElement(By.id("condition")).click();
        driver.findElement(By.name("nextButton")).click();
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

    public static String startStopBatch() {
        isStoped = ! isStoped;
        return "Batch is now stopped : " + isStoped;
    }

    /**
     * @param  target  hour to check
     * @param  start   interval start
     * @param  end     interval end
     * @return true    true if the given hour is between
     */
    public static boolean isHourInInterval(String target, String start, String end) {
        return ((target.compareTo(start) >= 0)
                && (target.compareTo(end) <= 0));
    }

    /**
     * @param  start   interval start
     * @param  end     interval end
     * @return true    true if the current hour is between
     */
    public static boolean isNowInInterval(String start, String end) {
        return isHourInInterval
                (getCurrentHour(), start, end);
    }

    public static String getCurrentHour() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdfHour = new SimpleDateFormat(HOUR_FORMAT);
        String hour = sdfHour.format(cal.getTime());
        return hour;
    }



    // failedTrials = trialsIndex - trialsNumber;

    /*    if (trialsIndex >= trialsNumber + maxTrials) {
            failedTrials += maxTrials;
            logger.info("[Rappel] - " + failedTrials + " tentatives échouées après " + trialsNumber + " essais aboutis");
            try {
                logger.info(TelegramMessageSender.sendMessage("[Rappel] - " + failedTrials + " tentatives échouées après " + trialsNumber + " essais aboutis"));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            trialsIndex = trialsNumber;
        }
        */
}
