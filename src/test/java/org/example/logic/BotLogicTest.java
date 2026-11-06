package org.example.logic;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.example.catalog.Movie;
import org.example.catalog.MovieCatalog;
import org.example.storage.WatchlistStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Модульные тесты логики диалога {@link BotLogic}. Тесты не используют ни Telegram, ни интернет:
 * каталог и хранилище подменены.
 */
class BotLogicTest {

    /** Идентификатор первого пользователя в тестах. */
    private final long firstUserId = 1L;

    /** Идентификатор второго пользователя в тестах. */
    private final long secondUserId = 2L;

    /** Ожидаемый ответ на /list при пустом списке. */
    private static final String EMPTY_LIST = "Список пока пуст. Найдите фильм командой /find <название>";

    /** Ожидаемый текст справки по командам. */
    private static final String HELP = "Привет! Я веду список фильмов «посмотреть позже».\n"
            + "Команды:\n"
            + "/remove <название> — удалить фильм\n"
            + "/list — показать список\n"
            + "/find <название> — найти фильм на Кинопоиске\n"
            + "/save <номер> — добавить в список фильм из последнего поиска\n"
            + "/pick [после <год>] — посоветовать фильм из списка\n"
            + "/help — показать эту справку";

    /** Ожидаемый ответ, если фильм уже есть в списке. */
    private static final String ALREADY_IN_LIST = "Этот фильм уже есть в списке";

    /** Ожидаемый ответ на /find «Матрица» (каталог знает четыре фильма, показываются три). */
    private static final String MATRIX_RESULTS = "Найдено:\n"
            + "1. Матрица (1999), рейтинг 8.5\n"
            + "2. Матрица: Перезагрузка (2003), рейтинг 7.2\n"
            + "3. Матрица: Революция (2003), рейтинг 6.8";

    /** Ожидаемый ответ на /find «Остров»: три разных фильма, два из них одного года. */
    private static final String ISLAND_RESULTS = "Найдено:\n"
            + "1. Остров (2005), рейтинг 7.5\n"
            + "2. Остров (2010), рейтинг 6.9\n"
            + "3. Остров (2005), рейтинг 8.1";

    /** Заготовленные ответы подменённого каталога: запрос в нижнем регистре и найденные фильмы. */
    private final Map<String, List<Movie>> catalogAnswers = new HashMap<>();

    /** Проверяемая логика; создаётся заново с пустым хранилищем перед каждым тестом. */
    private BotLogic logic;

    /**
     * Создаёт подменённый каталог без интернета и логику с пустым хранилищем перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        catalogAnswers.put("матрица", List.of(
                new Movie(1, "Матрица", 1999, 8.5),
                new Movie(2, "Матрица: Перезагрузка", 2003, 7.2),
                new Movie(3, "Матрица: Революция", 2003, 6.8),
                new Movie(4, "Матрица: Воскрешение", 2021, 5.6)));
        catalogAnswers.put("аватар", List.of(new Movie(5, "Аватар", 2009, 7.9)));
        catalogAnswers.put("интерстеллар", List.of(new Movie(9, "Интерстеллар", 2014, 8.6)));
        catalogAnswers.put("остров", List.of(
                new Movie(10, "Остров", 2005, 7.5),
                new Movie(11, "Остров", 2010, 6.9),
                new Movie(12, "Остров", 2005, 8.1)));
        catalogAnswers.put("дубль", List.of(
                new Movie(20, "Дубль А", 2000, 7.0),
                new Movie(21, "Дубль Б", 2001, 7.0)));
        MovieCatalog catalog = (title, limit) -> {
            List<Movie> found = catalogAnswers.getOrDefault(title.toLowerCase(Locale.ROOT), List.of());
            return found.subList(0, Math.min(limit, found.size()));
        };
        logic = new BotLogic(new FakeStorage(), catalog);
    }

    /**
     * Удаление по названию без учёта регистра и лишних пробелов: фильм исчезает из списка.
     */
    @Test
    void removeByTitle() {
        saveFirstFound(firstUserId, "Матрица");

        Assertions.assertEquals("1. Матрица (1999)", logic.handleMessage(firstUserId, "/list"));
        Assertions.assertEquals("Фильм удалён", logic.handleMessage(firstUserId, "/remove   МАТРИЦА"));
        Assertions.assertEquals(EMPTY_LIST, logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Ошибки удаления: название не указано или такого фильма нет в списке; список не меняется.
     */
    @Test
    void removeErrors() {
        saveFirstFound(firstUserId, "Матрица");

        Assertions.assertEquals("Укажите название: /remove <название>", logic.handleMessage(firstUserId, "/remove"));
        Assertions.assertEquals("Такого фильма нет в списке", logic.handleMessage(firstUserId, "/remove Аватар"));
        Assertions.assertEquals("1. Матрица (1999)", logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Список нумеруется с единицы и сохраняет порядок добавления; удаление не затрагивает остальные фильмы.
     */
    @Test
    void listOrder() {
        saveFirstFound(firstUserId, "Матрица");
        saveFirstFound(firstUserId, "Аватар");
        saveFirstFound(firstUserId, "Интерстеллар");
        Assertions.assertEquals("1. Матрица (1999)\n2. Аватар (2009)\n3. Интерстеллар (2014)",
                logic.handleMessage(firstUserId, "/list"));

        logic.handleMessage(firstUserId, "/remove Аватар");
        Assertions.assertEquals("1. Матрица (1999)\n2. Интерстеллар (2014)", logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Списки пользователей независимы: один и тот же фильм можно сохранить обоим.
     */
    @Test
    void separateLists() {
        saveFirstFound(firstUserId, "Матрица");
        Assertions.assertEquals(EMPTY_LIST, logic.handleMessage(secondUserId, "/list"));

        saveFirstFound(secondUserId, "Матрица");
        logic.handleMessage(firstUserId, "/remove Матрица");
        Assertions.assertEquals("1. Матрица (1999)", logic.handleMessage(secondUserId, "/list"));
    }

    /**
     * Если Кинопоиск недоступен, /find отвечает понятным сообщением, а бот не падает.
     */
    @Test
    void catalogFailure() {
        MovieCatalog broken = (title, limit) -> {
            throw new IOException("нет связи");
        };
        BotLogic brokenLogic = new BotLogic(new FakeStorage(), broken);

        Assertions.assertEquals("Не удалось связаться с Кинопоиском. Попробуйте позже",
                brokenLogic.handleMessage(firstUserId, "/find Матрица"));
        Assertions.assertEquals(EMPTY_LIST, brokenLogic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Команды не зависят от регистра и лишних пробелов, суффикс с именем бота игнорируется.
     */
    @Test
    void normalizedCommands() {
        Assertions.assertEquals(MATRIX_RESULTS, logic.handleMessage(firstUserId, "  /FIND    матрица "));
        Assertions.assertEquals("Фильм «Матрица» (1999) добавлен в список",
                logic.handleMessage(firstUserId, "/save@FirstReturnBot 1"));
        Assertions.assertEquals("1. Матрица (1999)", logic.handleMessage(firstUserId, "/List@FirstReturnBot"));
    }

    /**
     * /start и /help (в любом регистре и с пробелами) показывают полную справку по всем командам.
     */
    @Test
    void help() {
        for (String input : new String[] {"/start", "/help", " /HELP "}) {
            Assertions.assertEquals(HELP, logic.handleMessage(firstUserId, input));
        }
    }

    /**
     * Пустое сообщение (null, пробелы, табуляция, перевод строки) получает отдельный ответ.
     */
    @Test
    void blankInput() {
        for (String input : new String[] {null, "", "   ", "\t", " \t\n "}) {
            Assertions.assertEquals("Вы отправили пустое сообщение.", logic.handleMessage(firstUserId, input));
        }
    }

    /**
     * Неизвестный текст и удалённая команда /add получают подсказку про /help и не меняют список.
     */
    @Test
    void unknownInput() {
        for (String input : new String[] {"Матрица", "/unknown", "/add Матрица", "/", "find Матрица"}) {
            Assertions.assertEquals("Не понял вас. Список команд: /help", logic.handleMessage(firstUserId, input));
        }
        Assertions.assertEquals(EMPTY_LIST, logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Поиск, сохранение найденного фильма и просмотр списка (пример 1). Результаты идут по одному
     * фильму в строке, показываются не больше трёх, /save работает с последним поиском.
     */
    @Test
    void findAndSave() {
        Assertions.assertEquals(MATRIX_RESULTS, logic.handleMessage(firstUserId, "/find Матрица"));
        Assertions.assertEquals("Фильм «Матрица» (1999) добавлен в список", logic.handleMessage(firstUserId, "/save 1"));
        Assertions.assertEquals("1. Матрица (1999)", logic.handleMessage(firstUserId, "/list"));

        logic.handleMessage(firstUserId, "/find Аватар");
        Assertions.assertEquals("Фильм «Аватар» (2009) добавлен в список", logic.handleMessage(firstUserId, "/save 1"));
    }

    /**
     * /find без названия просит его указать, а при пустой выдаче отвечает, что ничего не найдено.
     */
    @Test
    void findErrors() {
        Assertions.assertEquals("Укажите название: /find <название>", logic.handleMessage(firstUserId, "/find"));
        Assertions.assertEquals("Ничего не найдено", logic.handleMessage(firstUserId, "/find Квантовый ляпсус"));
    }

    /**
     * /save без поиска (и после поиска, ничего не давшего) просит сначала найти фильм;
     * результаты поиска у каждого пользователя свои.
     */
    @Test
    void saveWithoutSearch() {
        String noSearch = "Сначала найдите фильм: /find <название>";
        Assertions.assertEquals(noSearch, logic.handleMessage(firstUserId, "/save 1"));

        logic.handleMessage(firstUserId, "/find Квантовый ляпсус");
        Assertions.assertEquals(noSearch, logic.handleMessage(firstUserId, "/save 1"));

        logic.handleMessage(firstUserId, "/find Матрица");
        Assertions.assertEquals(noSearch, logic.handleMessage(secondUserId, "/save 1"));
    }

    /**
     * Номер вне диапазона найденных фильмов не сохраняет ничего и называется в ответе.
     */
    @Test
    void saveOutOfRange() {
        logic.handleMessage(firstUserId, "/find Матрица");

        for (String number : new String[] {"0", "4", "-1", "7"}) {
            Assertions.assertEquals("В результатах поиска нет фильма с номером " + number,
                    logic.handleMessage(firstUserId, "/save " + number));
        }
        Assertions.assertEquals(EMPTY_LIST, logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Номер, который не является целым числом, или лишние слова дают подсказку формата.
     */
    @Test
    void saveInvalidNumber() {
        logic.handleMessage(firstUserId, "/find Матрица");

        for (String input : new String[] {"/save", "/save первый", "/save 1.5", "/save 1 2"}) {
            Assertions.assertEquals("Укажите номер: /save <номер>", logic.handleMessage(firstUserId, input));
        }
        Assertions.assertEquals(EMPTY_LIST, logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Сохраняется сам фильм с его идентификатором: одноимённые фильмы не путаются, а один и тот же
     * фильм повторно не добавляется.
     */
    @Test
    void sameTitleById() {
        Assertions.assertEquals(ISLAND_RESULTS, logic.handleMessage(firstUserId, "/find Остров"));
        Assertions.assertEquals("Фильм «Остров» (2010) добавлен в список", logic.handleMessage(firstUserId, "/save 2"));
        logic.handleMessage(firstUserId, "/find Остров");
        Assertions.assertEquals("Фильм «Остров» (2005) добавлен в список", logic.handleMessage(firstUserId, "/save 1"));
        Assertions.assertEquals(ALREADY_IN_LIST, logic.handleMessage(firstUserId, "/save 1"));
        Assertions.assertEquals("1. Остров (2010)\n2. Остров (2005)", logic.handleMessage(firstUserId, "/list"));

        Assertions.assertEquals("Фильм удалён", logic.handleMessage(firstUserId, "/remove Остров"));
        Assertions.assertEquals("1. Остров (2005)", logic.handleMessage(firstUserId, "/list"));
    }

    /**
     * Подбор фильма из списка (пример 2): лучший по рейтингу, с ограничением по году и без;
     * по сохранённым фильмам, а не по первому результату поиска.
     */
    @Test
    void pick() {
        Assertions.assertEquals("В вашем списке нет подходящих фильмов", logic.handleMessage(firstUserId, "/pick"));

        saveFirstFound(firstUserId, "Матрица");
        saveFirstFound(firstUserId, "Аватар");
        Assertions.assertEquals("Советую посмотреть: Матрица (1999), рейтинг 8.5", logic.handleMessage(firstUserId, "/pick"));
        Assertions.assertEquals("Советую посмотреть: Аватар (2009), рейтинг 7.9",
                logic.handleMessage(firstUserId, "/pick ПОСЛЕ   2000"));

        saveFirstFound(firstUserId, "Интерстеллар");
        Assertions.assertEquals("Советую посмотреть: Интерстеллар (2014), рейтинг 8.6", logic.handleMessage(firstUserId, "/pick"));
        Assertions.assertEquals("В вашем списке нет подходящих фильмов", logic.handleMessage(firstUserId, "/pick после 2030"));

        logic.handleMessage(secondUserId, "/find Остров");
        logic.handleMessage(secondUserId, "/save 2");
        Assertions.assertEquals("Советую посмотреть: Остров (2010), рейтинг 6.9", logic.handleMessage(secondUserId, "/pick"));
    }

    /**
     * При равных рейтингах /pick советует тот фильм, который стоит в списке раньше.
     */
    @Test
    void pickEqualRatings() {
        logic.handleMessage(firstUserId, "/find Дубль");
        logic.handleMessage(firstUserId, "/save 2");
        logic.handleMessage(firstUserId, "/find Дубль");
        logic.handleMessage(firstUserId, "/save 1");

        Assertions.assertEquals("1. Дубль Б (2001)\n2. Дубль А (2000)", logic.handleMessage(firstUserId, "/list"));
        Assertions.assertEquals("Советую посмотреть: Дубль Б (2001), рейтинг 7.0",
                logic.handleMessage(firstUserId, "/pick"));
    }

    /**
     * Неверная запись аргумента /pick даёт подсказку формата.
     */
    @Test
    void pickWrongFormat() {
        for (String input : new String[] {"/pick после", "/pick после abc", "/pick до 2000", "/pick 2000",
                "/pick после 0", "/pick после 2000 года"}) {
            Assertions.assertEquals("Формат: /pick [после <год>]", logic.handleMessage(firstUserId, input));
        }
    }

    /**
     * Ищет фильм и сохраняет первый из найденных, как это делает пользователь командами /find и /save.
     * @param userId идентификатор пользователя
     * @param title название для поиска
     */
    private void saveFirstFound(long userId, String title) {
        logic.handleMessage(userId, "/find " + title);
        logic.handleMessage(userId, "/save 1");
    }

    /**
     * Хранилище в памяти для тестов логики: логике не нужна настоящая база данных.
     */
    private class FakeStorage implements WatchlistStorage {

        /** Списки фильмов по идентификаторам пользователей. */
        private final Map<Long, List<Movie>> lists = new ConcurrentHashMap<>();

        /**
         * Возвращает копию списка пользователя.
         * @param userId идентификатор пользователя
         * @return фильмы в порядке добавления
         */
        @Override
        public List<Movie> getAll(long userId) {
            return List.copyOf(lists.getOrDefault(userId, List.of()));
        }

        /**
         * Добавляет фильм, если фильма с таким идентификатором у пользователя нет.
         * @param userId идентификатор пользователя
         * @param movie фильм
         * @return {@code true}, если фильм добавлен
         */
        @Override
        public boolean add(long userId, Movie movie) {
            List<Movie> movies = lists.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
            boolean exists = movies.stream().anyMatch(saved -> saved.id() == movie.id());
            return !exists && movies.add(movie);
        }

        /**
         * Удаляет фильм с указанным идентификатором.
         * @param userId идентификатор пользователя
         * @param movieId идентификатор фильма
         * @return {@code true}, если фильм был удалён
         */
        @Override
        public boolean remove(long userId, long movieId) {
            return lists.getOrDefault(userId, new CopyOnWriteArrayList<>()).removeIf(movie -> movie.id() == movieId);
        }
    }
}