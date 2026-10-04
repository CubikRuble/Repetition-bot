package org.example;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

public class BotLogicTest {

    @Test
    void testHandleMessage() {
        BotLogic botLogic = new BotLogic();
        long chatId = 12345L;

        // 1. Обычный текст
        SendMessage helloResponse = botLogic.handleMessage(chatId, "Привет");
        Assertions.assertEquals("Вы ввели \"Привет\"", helloResponse.getText());

        // 2. Команды /start и /help
        SendMessage startResponse = botLogic.handleMessage(chatId, "/start");
        Assertions.assertTrue(startResponse.getText().contains("Привет! Я эхо-бот."));

        // 3. Пустое сообщение
        SendMessage emptyResponse = botLogic.handleMessage(chatId, "   ");
        Assertions.assertEquals("Вы отправили пустое сообщение.", emptyResponse.getText());
    }
}