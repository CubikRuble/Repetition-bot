package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

public class TelegramBot extends TelegramLongPollingBot {

    @Override
    public String getBotUsername() {
        return "FirstReturnBot";
    }

    @Override
    public String getBotToken() {
        // Получаем токен из переменной окружения BOT_TOKEN
        String token = System.getenv("BOT_TOKEN");
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            var msg = update.getMessage();
            var reply = new SendMessage(String.valueOf(msg.getChatId()), "Вы написали: " + msg.getText());
            try {
                execute(reply);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}