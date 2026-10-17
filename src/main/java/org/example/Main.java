package org.example;

import org.example.logic.BotLogic;
import org.example.storage.JdbcWatchlistStorage;
import org.example.telegram.TelegramBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {

    public static void main(String[] args) throws Exception {
        JdbcWatchlistStorage storage = new JdbcWatchlistStorage("jdbc:sqlite:watchlist.db");
        Runtime.getRuntime().addShutdownHook(new Thread(storage::close));
        BotLogic logic = new BotLogic(storage);
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(new TelegramBot(logic));
        System.out.println("Бот запущен!");
    }
}