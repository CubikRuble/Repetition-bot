package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Функциональные тесты для проверки бизнес-логики бота {@link BotLogic}.
 */
class BotLogicTest {

    /** Экземпляр логики для выполнения тестов. */
    private static BotLogic botLogic;

    /** Тестовый ID чата. */
    private static final long CHAT_ID = 12345L;

    /**
     * Инициализация логики перед запуском всех тестов.
     */
    @BeforeAll
    static void prepare() {
        botLogic = new BotLogic();
    }

    /**
     * Проверка ответа на команду /start.
     */
    @Test
    void testStartCommand() {
        SendMessage response = botLogic.handleMessage(CHAT_ID, "/start");
        Assertions.assertTrue(response.getText().contains("Привет! Я эхо-бот."));
    }

    /**
     * Проверка ответа на команду /help.
     */
    @Test
    void testHelpCommand() {
        SendMessage response = botLogic.handleMessage(CHAT_ID, "/help");
        Assertions.assertTrue(response.getText().contains("Я умею:"));
    }

    /**
     * Проверка эхо-ответа на обычный текст.
     */
    @Test
    void testTextMessage() {
        SendMessage response = botLogic.handleMessage(CHAT_ID, "Привет");
        Assertions.assertEquals("Вы ввели \"Привет\"", response.getText());
    }

    /**
     * Проверка обработки пустого сообщения.
     */
    @Test
    void testEmptyMessage() {
        SendMessage response = botLogic.handleMessage(CHAT_ID, "   ");
        Assertions.assertEquals("Вы отправили пустое сообщение.", response.getText());
    }
}