package org.auto.appointments;

import org.apache.log4j.Logger;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.io.IOException;

public class AppointmentReminderJob implements Job {

    final static Logger logger = Logger.getLogger(AppointmentReminderJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            logger.info(TelegramMessageSender.sendMessage("Essayer de vérifier la dispo des rendez-vous . " + AppointmentCheckerJob.trialsNumber));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
