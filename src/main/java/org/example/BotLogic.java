package org.example;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Класс, содержащий бизнес-логику обработки текстовых сообщений.
 */
public class BotLogic {

    /** Текст справки и приветствия. */
    private static final String HELP_TEXT = "Привет! Я эхо-бот.\nЯ умею повторять твои сообщения.";

    /**
     * Обрабатывает входящий текст и возвращает объект ответа SendMessage.
     *
     * @param chatId ID чата
     * @param text входящий текст
     * @return сформированное сообщение для отправки
     */
    public SendMessage handleMessage(long chatId, String text) {
        SendMessage response = new SendMessage();
        response.setChatId(String.valueOf(chatId));

        // 1. Проверка на пустое сообщение или пробелы
        if (text == null || text.isBlank()) {
            response.setText("Вы отправили пустое сообщение.");
            return response;
        }

        String trimmed = text.trim();

        // 2. Проверка на команды /start и /help
        if ("/start".equals(trimmed) || "/help".equals(trimmed)) {
            response.setText(HELP_TEXT);
            return response;
        }

        // 3. Эхо-ответ на обычный текст
        response.setText("Вы ввели \"" + trimmed + "\"");
        return response;
    }
}