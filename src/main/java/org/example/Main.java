package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Главный класс приложения, предназначенный для инициализации и запуска Telegram-бота.
 */
public class Main {

    /**
     * Точка входа в программу. Создает сессию API и регистрирует бота.
     *
     * @param args аргументы командной строки
     * @throws Exception если возникает ошибка при инициализации сессии TelegramBotsApi
     */
    public static void main(String[] args) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new TelegramBot());
        System.out.println("Бот запущен!");
    }
}