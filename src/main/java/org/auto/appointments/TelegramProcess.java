package org.auto.appointments;
import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.GetUpdates;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.GetUpdatesResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TelegramProcess {

    private static final String CHAT_ID = "1002058034";
    private static final String TOKEN = "1210076443:AAF0pExJ1FNSgWMufDefv7YXLJUArG3avJE";


    public static void main(String[] args) {
        System.out.println("Starting");

        try {
            new EchoBot().run("token");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class EchoBot {

        private static int LONG_POOLING_TIMEOUT_IN_SECONDS = 7;

        private AtomicInteger updateId = new AtomicInteger(0);

        public void run(String token) throws InterruptedException {
            // TelegramBot bot = TelegramBotAdapter.build(token);
            TelegramBot bot = new TelegramBot(TOKEN);

            int updateId = 0;

            int timeoutInSecond = 7;

            while (true) {
                System.out.println("Checking for updates...");
                GetUpdatesResponse updatesResponse = bot.execute(
                        new GetUpdates().limit(100).offset(updateId).timeout(timeoutInSecond));

                List<Update> updates = updatesResponse.updates();
                if (updates.size() > 0) {
                    for (Update update : updates) {
                        System.out.println("Update: " + update);
                        if (update.message() != null) {
                            bot.execute(
                                    new SendMessage(
                                            update.message().from().id(),
                                            String.format(
                                                    "You said %s",
                                                    update.message().text() == null
                                                            ? "nothing"
                                                            : update.message().text())));
                        }
                        updateId = update.updateId() + 1;
                    }
                }
            }

        }
    }
}
