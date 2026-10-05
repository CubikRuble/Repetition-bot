package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
/**
 * Класс Telegram-бота, отвечающий за взаимодействие с API TelegramBots.
 * Принимает обновления от пользователей и отправляет сформированные ответы.
 */
public class TelegramBot extends TelegramLongPollingBot {

    /**
     * Имя бота, зарегистрированное в Telegram.
     */
    private static final String BOT_USERNAME = "FirstReturnBot";

    /**
     * Название переменной окружения, в которой хранится токен бота.
     */
    private static final String BOT_TOKEN_ENV_VAR = "BOT_TOKEN";

    /**
     * Экземпляр класса бизнес-логики для обработки входящих сообщений.
     */
    private final BotLogic botLogic = new BotLogic();

    /**
     * Возвращает имя бота в Telegram.
     *
     * @return имя пользователя бота
     */
    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    /**
     * Считывает и возвращает API-токен бота из переменной окружения.
     *
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
     * Метод-обработчик входящих обновлений от Telegram API.
     * Передает текст входящего сообщения в логику бота и отправляет сформированный ответ.
     *
     * @param update объект, содержащий данные о новом событии (сообщении) в Telegram
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message msg = update.getMessage();

            SendMessage response = botLogic.handleMessage(msg.getChatId(), msg.getText());

            try {
                execute(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}