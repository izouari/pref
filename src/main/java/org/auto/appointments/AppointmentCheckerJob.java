package org.auto.appointments;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.lang3.SerializationUtils;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AppointmentCheckerJob implements Job {

    final static Logger logger = Logger.getLogger(AppointmentCheckerJob.class);
    private static String  HOUR_FORMAT = "HH:mm";
    private static String start = "23:49";
    private static String end   = "23:59";
    public static int trialsNumber = 0;
    public static int trialsIndex = 0;
    public static int maxTrials = 100;
    public static int failedTrials = 0;

    public static String guichet1 = "planning5955";
    public static String guichet2 = "planning5968";
    public static String guichet3 = "planning5973";

    public static String guichetName1 = "GUICHET 21";
    public static String guichetName2 = "GUICHET 22";
    public static String guichetName3 = "GUICHET 24";

    public static boolean isStoped = false;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        if (isStoped) {
            return;
        }
        trialsIndex ++;

        logger.info( "[" + trialsIndex +"] - [" + jobExecutionContext.getJobDetail().getKey() +"] - CHECKING APPOINTMENT ");

        try {
            logger.info(takeAppointment());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
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
        Future<String> futureTask1 = executor.submit(() -> takeAppointmentByGuichet(pageGuichet1, guichet1, guichetName1));

        Thread.sleep(9000);

        Future<String> futureTask2 = executor.submit(() -> takeAppointmentByGuichet(pageGuichet2, guichet2, guichetName2));

        Thread.sleep(11000);

        Future<String> futureTask3 = executor.submit(() -> takeAppointmentByGuichet(pageGuichet3, guichet3, guichetName3));

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

        trialsNumber ++;

        if (isNowInInterval(start, end)) {
            logger.info(" [Statut du jour] -  Nombre de tentatives effectuées : " + trialsNumber);
            try {
                logger.info(TelegramMessageSender.sendMessage("[Récap du jour] -  Le nombre de tentatives effectuées : " + trialsNumber));
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            trialsNumber = 0;
            trialsIndex = 0;
            failedTrials = 0;
        }

        return "\n"+ "["+ guichetName1 +"] - " + guichet1Result + "\n" +
                "["+ guichetName2 +"] - " + guichet2Result + "\n" +
                "["+ guichetName3 +"] - " + guichet3Result;

    }


    private static String takeAppointmentByGuichet(HtmlPage page , String guichet, String guichetName) throws InterruptedException, IOException {

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
            // logger.info( guichet + " : " + responseMsg.getTextContent().trim());
            return responseMsg.getTextContent().trim();

        } else if (takenMsg.getTextContent().contains("Description de la nature")) {

            logger.info("RENDEZ-VOUS DISPO : \n " + takenMsg.getTextContent());

            TelegramMessageSender.sendMessage("YEEES :D  RENDEZ-VOUS DISPO DANS LE " + guichetName);
            return "YEEES :D  RENDEZ-VOUS DISPO DANS CE GUICHET \n Lien du rendez-vous: http://www.val-de-marne.gouv.fr/booking/create/4963/1" ;

        } else {
            // logger.info("Reponse : " + takenMsg.getTextContent());
            return "Réponse non reconnue. Requete redirigée vers la page d'accueil";
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

}
