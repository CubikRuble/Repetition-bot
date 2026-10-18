package org.example;

import org.example.logic.BotLogic;
import org.example.storage.JdbcWatchlistStorage;
import org.example.telegram.TelegramBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Точка входа: создаёт хранилище, логику диалога и Telegram-бота и запускает их.
 */
public class Main {

    /** Адрес базы данных SQLite; файл создаётся в рабочей директории при первом запуске. */
    private static final String DATABASE_URL = "jdbc:sqlite:watchlist.db";

    /**
     * Запускает бота. Токен берётся из переменной окружения {@code BOT_TOKEN}.
     *
     * @param args аргументы командной строки (не используются)
     * @throws Exception если не удалось зарегистрировать бота в Telegram
     */
    public static void main(String[] args) throws Exception {
        JdbcWatchlistStorage storage = new JdbcWatchlistStorage(DATABASE_URL);
        Runtime.getRuntime().addShutdownHook(new Thread(storage::close));
        BotLogic botLogic = new BotLogic(storage);
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new TelegramBot(botLogic));
        System.out.println("Бот запущен!");
    }
}
