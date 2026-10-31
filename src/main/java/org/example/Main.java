package org.example;

import org.example.catalog.KinopoiskCatalog;
import org.example.catalog.MovieCatalog;
import org.example.logic.BotLogic;
import org.example.storage.JdbcWatchlistStorage;
import org.example.storage.WatchlistStorage;
import org.example.telegram.TelegramBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Главный класс приложения: собирает слои бота (хранилище, каталог фильмов, логика,
 * Telegram-адаптер) и запускает бота.
 */
public class Main {

    /** Адрес базы данных SQLite: файл создаётся в рабочей папке при первом запуске. */
    private static final String DATABASE_URL = "jdbc:sqlite:watchlist.db";

    /** Название переменной окружения, в которой хранится токен API Кинопоиска. */
    private static final String KINOPOISK_TOKEN_ENV_VAR = "KINOPOISK_API_TOKEN";

    /**
     * Точка входа в программу. Создаёт хранилище и каталог, передаёт их в логику
     * и регистрирует бота в Telegram.
     * @param args аргументы командной строки
     * @throws Exception если возникает ошибка при инициализации сессии TelegramBotsApi
     */
    public static void main(String[] args) throws Exception {
        String kinopoiskToken = System.getenv(KINOPOISK_TOKEN_ENV_VAR);
        if (kinopoiskToken == null || kinopoiskToken.isBlank()) {
            throw new IllegalStateException("Переменная окружения " + KINOPOISK_TOKEN_ENV_VAR + " не задана!");
        }
        WatchlistStorage storage = new JdbcWatchlistStorage(DATABASE_URL);
        MovieCatalog catalog = new KinopoiskCatalog(kinopoiskToken);
        BotLogic botLogic = new BotLogic(storage, catalog);
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new TelegramBot(botLogic));
        System.out.println("Бот запущен!");
    }
}
