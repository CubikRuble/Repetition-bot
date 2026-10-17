package org.example.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.example.storage.WatchlistStorage;

public class BotLogic {

    private final WatchlistStorage storage;

    public BotLogic(WatchlistStorage storage) {
        this.storage = storage;
    }

    public String handleMessage(long userId, String text) {
        if (text == null || text.isBlank()) {
            return "Вы отправили пустое сообщение.";
        }

        String[] parts = text.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String title = parts.length > 1 ? parts[1].strip().replaceAll("\\s+", " "): "";

        if (command.contains("@")) {
            command = command.substring(0, command.indexOf('@'));
        }

        return switch (command) {
            case "/start", "/help" -> """
                    Привет! Я - Movie Bot. Бот для просмотра фильмов».
                    /add <название> — добавить фильм
                    /remove <название> — удалить фильм
                    /list — показать список
                    /help — помощь
                    """;
            case "/add" -> addMovie(userId, title);
            case "/remove" -> removeMovie(userId, title);
            case "/list" -> showList(userId);
            default -> "Не понял вас. Список команд: /help";
        };
    }

    private String addMovie(long userId, String title) {
        if (title.isEmpty()) {
            return "Укажите название: /add <название>";
        }

        if (findTitle(userId, title).isPresent()) {
            return "Этот фильм уже есть в списке";
        }

        storage.add(userId, title);
        return "Фильм добавлен";
    }

    private String removeMovie(long userId, String title) {
        if (title.isEmpty()) {
            return "Укажите название: /remove <название>";
        }

        Optional<String> saved = findTitle(userId, title);

        if (saved.isEmpty()) {
            return "Такого фильма нет в списке";
        }

        storage.remove(userId, saved.get());
        return "Фильм удалён";
    }

    private String showList(long userId) {
        List<String> titles = storage.getAll(userId);

        if (titles.isEmpty()) {
            return "Список пока пуст. Добавь фильм командой /add <название>";
        }

        List<String> result = new ArrayList<>();

        for (int i = 0; i < titles.size(); i++) {
            result.add((i + 1) + ". " + titles.get(i));
        }

        return String.join("\n", result);
    }

    private Optional<String> findTitle(long userId, String title) {
        for (String saved : storage.getAll(userId)) {
            if (saved.equalsIgnoreCase(title)) {
                return Optional.of(saved);
            }
        }

        return Optional.empty();
    }
}