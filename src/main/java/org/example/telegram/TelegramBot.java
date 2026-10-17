package org.example.telegram;

import org.example.logic.BotLogic;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class TelegramBot extends TelegramLongPollingBot {

    private final BotLogic logic;

    public TelegramBot(BotLogic logic) {
        this.logic = logic;
    }

    @Override
    public String getBotUsername() {
        return "FirstReturnBot";
    }

    @Override
    public String getBotToken() {
        String token = System.getenv("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Не задана переменная окружения BOT_TOKEN");
        }
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message message = update.getMessage();
        long userId = message.getFrom() == null ? message.getChatId(): message.getFrom().getId();

        String answer = logic.handleMessage(userId, message.getText());

        SendMessage response = new SendMessage();
        response.setChatId(String.valueOf(message.getChatId()));
        response.setText(answer);

        try {
            execute(response);
        } catch (TelegramApiException e) {
            System.err.println("Не удалось отправить сообщение: " + e.getMessage());
        }
    }
}