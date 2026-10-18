package org.example.logic;

import java.util.List;
import java.util.Optional;
import org.example.storage.WatchlistStorage;

/**
 * Логика диалога: разбирает сообщение пользователя и формирует ответ бота.
 * Не знает о Telegram; данные хранит через {@link WatchlistStorage}.
 * У каждого пользователя свой список, поэтому диалоги разных пользователей независимы.
 */
public class BotLogic {

    /** Справка по командам. */
    private static final String HELP_TEXT = "Привет! Я веду список фильмов «посмотреть позже».\n"
            + "Команды:\n"
            + "/add <название> — добавить фильм\n"
            + "/remove <название> — удалить фильм\n"
            + "/list — показать список\n"
            + "/help — показать эту справку";

    /** Ответ на пустое сообщение. */
    private static final String EMPTY_MESSAGE_TEXT = "Вы отправили пустое сообщение.";

    /** Ответ на неизвестный ввод. */
    private static final String UNKNOWN_INPUT_TEXT = "Не понял вас. Список команд: /help";

    /** Ответ на /add без названия. */
    private static final String ADD_NO_TITLE_TEXT = "Укажите название: /add <название>";

    /** Ответ на /remove без названия. */
    private static final String REMOVE_NO_TITLE_TEXT = "Укажите название: /remove <название>";

    /** Ответ на /list при пустом списке. */
    private static final String EMPTY_LIST_TEXT = "Список пока пуст. Добавь фильм командой /add <название>";

    /** Хранилище списков фильмов. */
    private final WatchlistStorage storage;

    /**
     * Создаёт логику диалога поверх хранилища.
     * @param storage хранилище списков фильмов
     */
    public BotLogic(WatchlistStorage storage) {
        this.storage = storage;
    }

    /**
     * Обрабатывает сообщение пользователя.
     * @param userId идентификатор пользователя
     * @param text   текст сообщения (может быть {@code null})
     * @return текст ответа бота
     */
    public String handleMessage(long userId, String text) {
        if (text == null || text.isBlank()) {
            return EMPTY_MESSAGE_TEXT;
        }
        String[] parts = text.strip().split("\\s+", 2);
        String title = parts.length > 1 ? parts[1].strip().replaceAll("\\s+", " ") : "";

        return switch (parts[0].toLowerCase()) {
            case "/start", "/help" -> HELP_TEXT;
            case "/add" -> title.isEmpty() ? ADD_NO_TITLE_TEXT : addMovie(userId, title);
            case "/remove" -> title.isEmpty() ? REMOVE_NO_TITLE_TEXT : removeMovie(userId, title);
            case "/list" -> showList(userId);
            default -> UNKNOWN_INPUT_TEXT;
        };
    }

    /**
     * Добавляет фильм, если его ещё нет в списке пользователя.
     * @param userId идентификатор пользователя
     * @param title  название фильма
     * @return текст ответа бота
     */
    private String addMovie(long userId, String title) {
        if (findSavedTitle(userId, title).isPresent()) {
            return "Этот фильм уже есть в списке";
        }
        storage.add(userId, title);
        return "Фильм добавлен";
    }

    /**
     * Удаляет фильм из списка пользователя без учёта регистра.
     * @param userId идентификатор пользователя
     * @param title  название фильма
     * @return текст ответа бота
     */
    private String removeMovie(long userId, String title) {
        Optional<String> saved = findSavedTitle(userId, title);
        if (saved.isEmpty()) {
            return "Такого фильма нет в списке";
        }
        storage.remove(userId, saved.get());
        return "Фильм удалён";
    }

    /**
     * Формирует нумерованный список фильмов пользователя.
     * @param userId идентификатор пользователя
     * @return список или сообщение о том, что он пуст
     */
    private String showList(long userId) {
        List<String> titles = storage.getAll(userId);
        if (titles.isEmpty()) {
            return EMPTY_LIST_TEXT;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < titles.size(); i++) {
            result.append(i + 1).append(". ").append(titles.get(i)).append('\n');
        }
        return result.toString().strip();
    }

    /**
     * Ищет фильм в списке пользователя без учёта регистра.
     * @param userId идентификатор пользователя
     * @param title  искомое название
     * @return название в том виде, в котором оно сохранено
     */
    private Optional<String> findSavedTitle(long userId, String title) {
        return storage.getAll(userId).stream().filter(saved -> saved.equalsIgnoreCase(title)).findFirst();
    }
}
