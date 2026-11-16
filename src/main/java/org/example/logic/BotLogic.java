package org.example.logic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.example.catalog.Movie;
import org.example.catalog.MovieCatalog;
import org.example.storage.WatchlistEntry;
import org.example.storage.WatchlistStorage;

/**
 * Логика диалога бота: список «посмотреть позже», поиск фильмов в каталоге, оценки просмотренных
 * фильмов, статистика и подбор фильма. В списке хранятся записи {@link WatchlistEntry}: фильм
 * ({@link Movie}) с уникальным идентификатором Кинопоиска и оценка пользователя. Фильм с оценкой
 * считается просмотренным. Класс не зависит от Telegram: принимает идентификатор пользователя
 * и текст сообщения, возвращает текст ответа.
 */
public class BotLogic {

    /** Текст приветствия и справки по командам. */
    private static final String HELP_TEXT = "Привет! Я веду список фильмов «посмотреть позже».\n"
            + "Команды:\n"
            + "/remove <название> — удалить фильм\n"
            + "/list — показать список\n"
            + "/find <название> — найти фильм на Кинопоиске\n"
            + "/save <номер> — добавить в список фильм из последнего поиска\n"
            + "/pick [после <год>] — посоветовать непросмотренный фильм из списка\n"
            + "/rate <номер> <оценка> — оценить фильм из списка (от 1 до 10) и отметить его просмотренным\n"
            + "/stats — показать число просмотренных фильмов и среднюю оценку\n"
            + "/help — показать эту справку";

    /** Ответ, если по запросу ничего не найдено. */
    private static final String NOTHING_FOUND_TEXT = "Ничего не найдено";

    /** Ответ, если Кинопоиск не отвечает. */
    private static final String CATALOG_UNAVAILABLE_TEXT = "Не удалось связаться с Кинопоиском. Попробуйте позже";

    /** Ответ при попытке сохранить фильм, который уже есть в списке. */
    private static final String ALREADY_IN_LIST_TEXT = "Этот фильм уже есть в списке";

    /** Ответ на /rate, если команда записана неверно. */
    private static final String RATE_FORMAT_TEXT = "Формат: /rate <номер> <оценка>";

    /** Ответ на /rate, если оценка не является целым числом от 1 до 10. */
    private static final String RATING_RANGE_TEXT = "Оценка должна быть целым числом от 1 до 10";

    /** Ответ на /stats, если пользователь ещё не оценил ни одного фильма. */
    private static final String NO_RATINGS_TEXT = "Вы пока ничего не оценили";

    /** Сколько фильмов показывает команда /find. */
    private static final int findLimit = 3;

    /** Наименьшая допустимая оценка. */
    private static final int minRating = 1;

    /** Наибольшая допустимая оценка. */
    private static final int maxRating = 10;

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
            case "/remove" -> removeMovie(userId, argument);
            case "/list" -> showList(userId);
            case "/find" -> findMovies(userId, argument);
            case "/save" -> saveFound(userId, argument);
            case "/pick" -> pickMovie(userId, argument);
            case "/rate" -> rateMovie(userId, argument);
            case "/stats" -> showStats(userId);
            default -> "Не понял вас. Список команд: /help";
        };
    }

    /**
     * Удаляет фильм из списка пользователя по названию (без учёта регистра).
     * Если одноимённых фильмов несколько, удаляется первый из них.
     * @param userId идентификатор пользователя
     * @param title название; пустая строка, если пользователь его не указал
     * @return текст ответа
     */
    private String removeMovie(long userId, String title) {
        if (title.isEmpty()) {
            return "Укажите название: /remove <название>";
        }
        for (WatchlistEntry entry : storage.getAll(userId)) {
            if (entry.movie().title().equalsIgnoreCase(title)) {
                storage.remove(userId, entry.movie().id());
                return "Фильм удалён";
            }
        }
        return "Такого фильма нет в списке";
    }

    /**
     * Формирует нумерованный список фильмов пользователя, по одному в строке. У просмотренных
     * фильмов после года показывается оценка пользователя.
     * @param userId идентификатор пользователя
     * @return список или сообщение о том, что он пуст
     */
    private String showList(long userId) {
        List<WatchlistEntry> entries = storage.getAll(userId);
        if (entries.isEmpty()) {
            return "Список пока пуст. Найдите фильм командой /find <название>";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            WatchlistEntry entry = entries.get(i);
            String line = (i + 1) + ". " + entry.movie().title() + " (" + entry.movie().year() + ")";
            if (entry.isWatched()) {
                line += " — оценка " + entry.userRating();
            }
            lines.add(line);
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
     * Ставит оценку фильму из списка пользователя и тем самым отмечает его просмотренным.
     * Номер берётся из команды /list; повторная оценка заменяет прежнюю.
     * @param userId идентификатор пользователя
     * @param argument номер фильма и оценка через пробел; пустая строка, если их не указали
     * @return текст ответа
     */
    private String rateMovie(long userId, String argument) {
        String[] words = argument.split(" ");
        Integer number = words.length == 2 ? parseNumber(words[0]) : null;
        if (number == null) {
            return RATE_FORMAT_TEXT;
        }
        Integer rating = parseNumber(words[1]);
        if (rating == null || rating < minRating || rating > maxRating) {
            return RATING_RANGE_TEXT;
        }
        List<WatchlistEntry> entries = storage.getAll(userId);
        String notFoundText = "В списке нет фильма с номером " + number;
        if (number < 1 || number > entries.size()) {
            return notFoundText;
        }
        Movie movie = entries.get(number - 1).movie();
        if (!storage.rate(userId, movie.id(), rating)) {
            return notFoundText;
        }
        return "Фильм «" + movie.title() + "» отмечен как просмотренный, оценка " + rating;
    }

    /**
     * Считает просмотренные фильмы пользователя (те, у которых есть оценка) и их среднюю оценку.
     * @param userId идентификатор пользователя
     * @return число просмотренных фильмов и средняя оценка либо сообщение, что оценок нет
     */
    private String showStats(long userId) {
        int watchedCount = 0;
        int ratingSum = 0;
        for (WatchlistEntry entry : storage.getAll(userId)) {
            if (entry.isWatched()) {
                watchedCount++;
                ratingSum += entry.userRating();
            }
        }
        if (watchedCount == 0) {
            return NO_RATINGS_TEXT;
        }
        return String.format(Locale.ROOT, "Просмотрено фильмов: %d, средняя оценка: %.1f",
                watchedCount, (double) ratingSum / watchedCount);
    }

    /**
     * Советует непросмотренный фильм из списка пользователя с наивысшим рейтингом. Просмотренные
     * фильмы (с оценкой пользователя) пропускаются. Рейтинг и год берутся из сохранённых фильмов.
     * При равных рейтингах побеждает фильм, который стоит в списке раньше.
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
        for (WatchlistEntry entry : storage.getAll(userId)) {
            Movie movie = entry.movie();
            if (!entry.isWatched() && movie.year() >= minYear && (best == null || movie.rating() > best.rating())) {
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
