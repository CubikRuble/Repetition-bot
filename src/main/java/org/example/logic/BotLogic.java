package org.example.logic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.catalog.Movie;
import org.example.catalog.MovieCatalog;
import org.example.storage.WatchlistStorage;

/**
 * Логика диалога бота: список «посмотреть позже», поиск фильмов в каталоге и подбор фильма.
 * В списке хранятся объекты {@link Movie} с уникальным идентификатором Кинопоиска.
 * Класс не зависит от Telegram: принимает идентификатор пользователя и текст сообщения,
 * возвращает текст ответа.
 */
public class BotLogic {

    /** Текст приветствия и справки по командам. */
    private static final String HELP_TEXT = "Привет! Я веду список фильмов «посмотреть позже».\n"
            + "Команды:\n"
            + "/add <название> — добавить фильм\n"
            + "/remove <номер или название> — удалить фильм\n"
            + "/list — показать список\n"
            + "/find <название> — найти фильм на Кинопоиске\n"
            + "/save <номер> — добавить в список фильм из последнего поиска\n"
            + "/pick [после <год>] — посоветовать фильм из списка\n"
            + "/help — показать эту справку";

    /** Ответ, если по запросу ничего не найдено. */
    private static final String NOTHING_FOUND_TEXT = "Ничего не найдено";

    /** Ответ, если Кинопоиск не отвечает. */
    private static final String CATALOG_UNAVAILABLE_TEXT = "Не удалось связаться с Кинопоиском. Попробуйте позже";

    /** Ответ при попытке добавить фильм, который уже есть в списке. */
    private static final String ALREADY_IN_LIST_TEXT = "Этот фильм уже есть в списке";

    /** Сколько фильмов показывает команда /find. */
    private final int findLimit = 3;

    /** Хранилище, в котором лежат списки всех пользователей. */
    private final WatchlistStorage storage;

    /** Каталог, в котором ищутся фильмы. */
    private final MovieCatalog catalog;

    /** Результаты последнего поиска каждого пользователя, чтобы /save знала, что сохранять. */
    private final Map<Long, List<Movie>> lastSearch = new ConcurrentHashMap<>();

    /**
     * Создаёт логику бота, работающую с переданными хранилищем и каталогом.
     * @param storage хранилище списков пользователей
     * @param catalog каталог фильмов
     */
    public BotLogic(WatchlistStorage storage, MovieCatalog catalog) {
        this.storage = storage;
        this.catalog = catalog;
    }

    /**
     * Обрабатывает входящее сообщение и возвращает текст ответа.
     * Команды не чувствительны к регистру, суффикс вида {@code @имя_бота} игнорируется.
     * @param userId идентификатор пользователя, чей список обрабатывается
     * @param text текст сообщения, может быть {@code null}
     * @return текст ответа пользователю
     */
    public String handleMessage(long userId, String text) {
        if (text == null || text.isBlank()) {
            return "Вы отправили пустое сообщение.";
        }
        String[] parts = text.strip().split("\\s+", 2);
        String command = parts[0].split("@")[0].toLowerCase(Locale.ROOT);
        String argument = parts.length > 1 ? parts[1].strip().replaceAll("\\s+", " ") : "";

        return switch (command) {
            case "/start", "/help" -> HELP_TEXT;
            case "/add" -> addMovie(userId, argument);
            case "/remove" -> removeMovie(userId, argument);
            case "/list" -> showList(userId);
            case "/find" -> findMovies(userId, argument);
            case "/save" -> saveFound(userId, argument);
            case "/pick" -> pickMovie(userId, argument);
            default -> "Не понял вас. Список команд: /help";
        };
    }

    /**
     * Ищет фильм в каталоге по названию и добавляет в список первый найденный.
     * @param userId идентификатор пользователя
     * @param title название; пустая строка, если пользователь его не указал
     * @return текст ответа
     */
    private String addMovie(long userId, String title) {
        if (title.isEmpty()) {
            return "Укажите название: /add <название>";
        }
        try {
            List<Movie> found = catalog.search(title, 1);
            if (found.isEmpty()) {
                return NOTHING_FOUND_TEXT;
            }
            return storage.add(userId, found.get(0)) ? "Фильм добавлен" : ALREADY_IN_LIST_TEXT;
        } catch (IOException e) {
            return CATALOG_UNAVAILABLE_TEXT;
        }
    }

    /**
     * Удаляет фильм из списка пользователя. Фильм указывается номером из /list или названием;
     * если одноимённых фильмов несколько, по названию удаляется первый из них.
     * @param userId идентификатор пользователя
     * @param argument номер или название; пустая строка, если их не указали
     * @return текст ответа
     */
    private String removeMovie(long userId, String argument) {
        if (argument.isEmpty()) {
            return "Укажите название: /remove <название>";
        }
        List<Movie> movies = storage.getAll(userId);
        Integer number = parseNumber(argument);
        for (int i = 0; i < movies.size(); i++) {
            if (number != null && number == i + 1 || movies.get(i).title().equalsIgnoreCase(argument)) {
                storage.remove(userId, movies.get(i).id());
                return "Фильм удалён";
            }
        }
        return "Такого фильма нет в списке";
    }

    /**
     * Формирует нумерованный список фильмов пользователя, по одному в строке.
     * @param userId идентификатор пользователя
     * @return список или сообщение о том, что он пуст
     */
    private String showList(long userId) {
        List<Movie> movies = storage.getAll(userId);
        if (movies.isEmpty()) {
            return "Список пока пуст. Добавь фильм командой /add <название>";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < movies.size(); i++) {
            lines.add((i + 1) + ". " + movies.get(i).title() + " (" + movies.get(i).year() + ")");
        }
        return String.join("\n", lines);
    }

    /**
     * Ищет фильмы в каталоге, запоминает результаты для команды /save и показывает их
     * нумерованным списком, по одному фильму в строке.
     * @param userId идентификатор пользователя
     * @param title название; пустая строка, если пользователь его не указал
     * @return текст ответа
     */
    private String findMovies(long userId, String title) {
        if (title.isEmpty()) {
            return "Укажите название: /find <название>";
        }
        List<Movie> found;
        try {
            found = catalog.search(title, findLimit);
        } catch (IOException e) {
            return CATALOG_UNAVAILABLE_TEXT;
        }
        lastSearch.put(userId, List.copyOf(found));
        if (found.isEmpty()) {
            return NOTHING_FOUND_TEXT;
        }
        List<String> lines = new ArrayList<>(List.of("Найдено:"));
        for (int i = 0; i < found.size(); i++) {
            lines.add((i + 1) + ". " + describe(found.get(i)));
        }
        return String.join("\n", lines);
    }

    /**
     * Добавляет в список пользователя один из фильмов, найденных последним поиском.
     * @param userId идентификатор пользователя
     * @param argument номер фильма в результатах поиска; пустая строка, если его не указали
     * @return текст ответа
     */
    private String saveFound(long userId, String argument) {
        Integer number = parseNumber(argument);
        if (number == null) {
            return "Укажите номер: /save <номер>";
        }
        List<Movie> found = lastSearch.getOrDefault(userId, List.of());
        if (found.isEmpty()) {
            return "Сначала найдите фильм: /find <название>";
        }
        if (number < 1 || number > found.size()) {
            return "В результатах поиска нет фильма с номером " + number;
        }
        Movie movie = found.get(number - 1);
        if (!storage.add(userId, movie)) {
            return ALREADY_IN_LIST_TEXT;
        }
        return "Фильм «" + movie.title() + "» (" + movie.year() + ") добавлен в список";
    }

    /**
     * Советует фильм из списка пользователя с наивысшим рейтингом. Рейтинг и год берутся
     * из сохранённых фильмов. При равных рейтингах побеждает фильм, который стоит в списке раньше.
     * @param userId идентификатор пользователя
     * @param argument пустая строка или {@code после <год>}
     * @return текст ответа
     */
    private String pickMovie(long userId, String argument) {
        int minYear = 0;
        if (!argument.isEmpty()) {
            String[] words = argument.split(" ");
            Integer year = words.length == 2 && words[0].equalsIgnoreCase("после") ? parseNumber(words[1]) : null;
            if (year == null || year <= 0) {
                return "Формат: /pick [после <год>]";
            }
            minYear = year;
        }
        Movie best = null;
        for (Movie movie : storage.getAll(userId)) {
            if (movie.year() >= minYear && (best == null || movie.rating() > best.rating())) {
                best = movie;
            }
        }
        return best == null ? "В вашем списке нет подходящих фильмов" : "Советую посмотреть: " + describe(best);
    }

    /**
     * Описывает фильм одной строкой: название, год и рейтинг.
     * @param movie фильм
     * @return строка вида {@code Матрица (1999), рейтинг 8.5}
     */
    private String describe(Movie movie) {
        return String.format(Locale.ROOT, "%s (%d), рейтинг %.1f", movie.title(), movie.year(), movie.rating());
    }

    /**
     * Разбирает целое число из аргумента команды.
     * @param argument текст аргумента
     * @return число или {@code null}, если аргумент пуст или не является целым числом
     */
    private Integer parseNumber(String argument) {
        try {
            return Integer.parseInt(argument);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
