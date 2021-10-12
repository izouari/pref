package org.auto.appointments;


import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetUpdatesResponse;
import org.apache.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.IOException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.CronScheduleBuilder.cronSchedule;
public class Main {

    final static Logger logger = Logger.getLogger(Main.class);
    private static final String TOKEN = "2054033158:AAECSMRPvL3bDnBRbRpitY7GbTNGzTDkrXQ";

    public static void main(String[] args) throws IOException, InterruptedException, SchedulerException {
        // Creating a new instance of the HTML unit driver

        //Create instance of factory
        SchedulerFactory schedulerFactory=new StdSchedulerFactory();

        //Get schedular
        Scheduler scheduler= schedulerFactory.getScheduler();

        //Create JobDetail object specifying which Job you want to execute

        JobDetail dailyJob = newJob(AppointmentAtGuichet1Job.class)
                .withIdentity("dailyJob", "Rdv")
                .build();
        CronTrigger dailyTrigger = newTrigger()
                .withIdentity("dailyTrigger", "Rdv")
                .withSchedule(cronSchedule("0 0/3 10-21 ? * MON,TUE,WED,THU,FRI *"))
                .build();


       JobDetail midnightJob = newJob(AppointmentAtGuichet1Job.class)
                .withIdentity("midnightJob", "Rdv")
                .build();

        // Chaque jour entre minuit et 2
         CronTrigger midnightTrigger = newTrigger()
                .withIdentity("midnightTrigger", "Rdv")
                .withSchedule(cronSchedule("0 0/15 00-02 * * ?"))
                .build();


        JobDetail nightJob = newJob(AppointmentAtGuichet1Job.class)
                .withIdentity("nightJob", "Rdv")
                .build();

        //Associate Trigger to the Job
        CronTrigger nightTrigger = newTrigger()
                .withIdentity("nightTrigger", "Rdv")
                .withSchedule(cronSchedule("0 0/10 22-23 ? * MON,TUE,WED,THU,FRI *"))
                .build();


        //Pass JobDetail and trigger dependencies to schedular
        scheduler.scheduleJob(dailyJob, dailyTrigger);

        scheduler.scheduleJob(midnightJob, midnightTrigger);

        scheduler.scheduleJob(nightJob, nightTrigger);

        //Start schedular
        scheduler.start();

        new EchoBot().run();

        try {
            Object lock = new Object();
            int j = 0;
            synchronized (lock) {
                while (true) {
                    lock.wait();
                    j++;
                    if (j == 10000000000000L)
                        break;
                }
            }
        } catch (InterruptedException ex) {
        }

        logger.info("Shutting down appointment checker app ... ");

        scheduler.shutdown(true);


    }


    private static class EchoBot {

        public void run() throws InterruptedException, IOException {
            TelegramBot bot = new TelegramBot(TOKEN);

            int updateId = 0;

            int timeoutInSecond = 7;

            while (true) {
               //  System.out.println("Checking for updates...");
                GetUpdatesResponse updatesResponse = bot.execute(
                        new GetUpdates().limit(100).offset(updateId).timeout(timeoutInSecond));

                List<Update> updates = updatesResponse.updates();
                if (updates.size() > 0) {
                    for (Update update : updates) {
                        // System.out.println("Update: " + update);
                        if (update.message() != null) {
                            bot.execute(
                                    new SendMessage(
                                            update.message().from().id(),
                                            buildResponse(update.message().text()))
                                            );
                        }
                        updateId = update.updateId() + 1;
                    }
                }
            }

        }
    }


    private static String buildResponse (String text) throws IOException, InterruptedException {
        if (text == null) return "Tape un mot !!";

        if (text.equalsIgnoreCase("state") || text.equalsIgnoreCase("bonjour") || text.equalsIgnoreCase("oui") ) {

            return "[ " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +" ] \n Total d'essais: "
                    + (AppointmentCheckerJob.trialsIndex)  +
                    " \n Réussis : " + AppointmentAtGuichet1Job.trialsNumber +
                    " \n Echoués : " + (AppointmentAtGuichet1Job.trialsIndex - AppointmentAtGuichet1Job.trialsNumber) +
                    " \n Lien du rendez-vous: https://www.essonne.gouv.fr/booking/create/29728/0";


            /*+" ] \n Total d'essais Guichet 22: "
                    + (AppointmentAtGuichet2Job.trialsIndex)  +
                    " \n Réussis : " + AppointmentCheckerGuichet2Job.trialsNumber +
                    " \n Echoués : " + (AppointmentCheckerGuichet2Job.trialsIndex - AppointmentCheckerGuichet2Job.trialsNumber);*/


           //  return "Ok, une nouvelle tentative de rendez-vous est en cours... ";
        } else  if (text.equalsIgnoreCase("stop") || text.equalsIgnoreCase("start")) {
            return AppointmentAtGuichet1Job.startStopBatch();
        }
          else {
            try {
                 return AppointmentAtGuichet1Job.retryAppointment();
                 // Thread.sleep(2000);
                 //AppointmentTest.retryAppointment("22");
                 // return "Tentative de prise de rendez-vous en cours ...";

            } catch (Exception ex) {
                logger.warn("Tentative demandé par l'utilisateur échouée ", ex);
                return "Tentative échouée :( réessayer encore .. ";
            }
        }
    }

}
