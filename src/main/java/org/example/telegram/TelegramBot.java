package org.example.telegram;

import org.example.logic.BotLogic;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Адаптер Telegram: принимает сообщения от платформы, передаёт их в {@link BotLogic}
 * и отправляет ответ обратно в чат.
 */
public class TelegramBot extends TelegramLongPollingBot {

    /** Имя бота в Telegram. */
    private static final String BOT_USERNAME_ENV_VAR = "BOT_USERNAME";

    /** Имя переменной окружения, в которой хранится токен бота. */
    private static final String BOT_TOKEN_ENV_VAR = "BOT_TOKEN";

    /** Логика диалога, не зависящая от Telegram. */
    private final BotLogic botLogic;

    /**
     * Создаёт бота.
     * @param botLogic логика диалога, которой передаются все текстовые сообщения
     */
    public TelegramBot(BotLogic botLogic) {
        this.botLogic = botLogic;
    }

    @Override
    public String getBotUsername() {
        String username = System.getenv(BOT_USERNAME_ENV_VAR);
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Переменная окружения " + BOT_USERNAME_ENV_VAR + " не задана!");
        }
        return username;
    }

    /**
     * Возвращает токен из переменной окружения {@code BOT_TOKEN}.
     * @return токен бота
     * @throws IllegalStateException если переменная не задана
     */
    @Override
    public String getBotToken() {
        String token = System.getenv(BOT_TOKEN_ENV_VAR);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Переменная окружения " + BOT_TOKEN_ENV_VAR + " не задана!");
        }
        return token;
    }

    /**
     * Обрабатывает входящее обновление: текстовое сообщение передаётся в логику, ответ отправляется в тот же чат.
     * Диалоги разных пользователей различаются по идентификатору отправителя.
     * @param update обновление от Telegram
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long userId = message.getFrom() == null ? message.getChatId() : message.getFrom().getId();
            String replyText = botLogic.handleMessage(userId, message.getText());
            SendMessage response = new SendMessage();
            response.setChatId(String.valueOf(message.getChatId()));
            response.setText(replyText);
            try {
                execute(response);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }
}
