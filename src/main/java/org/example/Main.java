package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) throws Exception {
        new TelegramBotsApi(DefaultBotSession.class).registerBot(new TelegramBot());
        System.out.println("Бот запущен!");
    }
}