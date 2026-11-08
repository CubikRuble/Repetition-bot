package org.example.telegram;

import org.example.logic.BotLogic;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Telegram-адаптер: принимает обновления от Telegram, передаёт текст в логику бота
 * и отправляет полученный ответ в тот же чат. Вся работа с Telegram API сосредоточена здесь.
 */
public class TelegramBot extends TelegramLongPollingBot {

    /** Имя бота, зарегистрированное в Telegram. */
    private static final String BOT_USERNAME = "BOT_USERNAME";

    /** Название переменной окружения, в которой хранится токен бота. */
    private static final String BOT_TOKEN_ENV_VAR = "BOT_TOKEN";

    /** Логика диалога, которой адаптер передаёт тексты сообщений. */
    private final BotLogic botLogic;

    /**
     * Создаёт адаптер для переданной логики бота.
     * @param botLogic логика диалога
     */
    public TelegramBot(BotLogic botLogic) {
        this.botLogic = botLogic;
    }

    /**
     * Возвращает имя бота в Telegram.
     * @return имя пользователя бота
     */
    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    /**
     * Считывает и возвращает API-токен бота из переменной окружения.
     * @return токен доступа Telegram API
     * @throws IllegalStateException если переменная окружения BOT_TOKEN не задана или пуста
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
     * Обрабатывает входящее обновление: берёт текст сообщения, получает ответ от логики
     * и отправляет его в чат. Список фильмов определяется отправителем сообщения,
     * поэтому в групповом чате у каждого участника свой список.
     * @param update данные о новом событии в Telegram
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
