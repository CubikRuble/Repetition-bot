package org.example.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.example.catalog.Movie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Тесты хранилища {@link JdbcWatchlistStorage}. Каждый тест работает с файлом базы
 * во временной папке, которую JUnit удаляет после теста.
 */
class JdbcWatchlistStorageTest {

    /** Идентификатор первого пользователя в тестах. */
    private final long firstUserId = 1L;

    /** Идентификатор второго пользователя в тестах. */
    private final long secondUserId = 2L;

    /** Временная папка, в которой лежит файл базы для текущего теста. */
    @TempDir
    Path tempDirectory;

    /** Проверяемое хранилище; создаётся заново перед каждым тестом. */
    private JdbcWatchlistStorage storage;

    /**
     * Создаёт хранилище поверх пустой базы данных во временной папке.
     */
    @BeforeEach
    void setUp() {
        storage = new JdbcWatchlistStorage(urlOf("watchlist.db"));
    }

    /** Фильм «Матрица» для тестов. */
    private final Movie matrix = new Movie(301, "Матрица", 1999, 8.5);

    /** Фильм «Аватар» для тестов. */
    private final Movie avatar = new Movie(251733, "Аватар", 2009, 7.9);

    /** Фильм «Дюна» 2021 года для тестов. */
    private final Movie dune = new Movie(409424, "Дюна", 2021, 7.8);

    /**
     * У пользователя, которого хранилище не знает, список пустой.
     */
    @Test
    void emptyForUnknownUser() {
        Assertions.assertEquals(List.of(), storage.getAll(firstUserId));
    }

    /**
     * Фильм возвращается целиком: идентификатор, название, год и рейтинг сохраняются без потерь.
     */
    @Test
    void storesAllFields() {
        Assertions.assertTrue(storage.add(firstUserId, matrix));

        Assertions.assertEquals(unrated(matrix), storage.getAll(firstUserId));
    }

    /**
     * Фильмы возвращаются в том порядке, в котором их добавили.
     */
    @Test
    void keepsInsertionOrder() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.add(firstUserId, dune);

        Assertions.assertEquals(unrated(matrix, avatar, dune), storage.getAll(firstUserId));
    }

    /**
     * Один и тот же фильм нельзя добавить пользователю дважды.
     */
    @Test
    void noDuplicates() {
        Assertions.assertTrue(storage.add(firstUserId, matrix));
        Assertions.assertFalse(storage.add(firstUserId, matrix));

        Assertions.assertEquals(unrated(matrix), storage.getAll(firstUserId));
    }

    /**
     * Одноимённые фильмы с разными идентификаторами считаются разными фильмами.
     */
    @Test
    void sameTitleDifferentIds() {
        Movie islandOld = new Movie(10, "Остров", 2005, 7.5);
        Movie islandNew = new Movie(11, "Остров", 2010, 6.9);

        Assertions.assertTrue(storage.add(firstUserId, islandOld));
        Assertions.assertTrue(storage.add(firstUserId, islandNew));

        Assertions.assertEquals(unrated(islandOld, islandNew), storage.getAll(firstUserId));
    }

    /**
     * Списки разных пользователей не пересекаются, а один фильм может быть у обоих.
     */
    @Test
    void usersAreIndependent() {
        storage.add(firstUserId, matrix);
        storage.add(secondUserId, avatar);
        Assertions.assertTrue(storage.add(secondUserId, matrix));

        Assertions.assertEquals(unrated(matrix), storage.getAll(firstUserId));
        Assertions.assertEquals(unrated(avatar, matrix), storage.getAll(secondUserId));
    }

    /**
     * Возвращённый список нельзя изменить, а последующие добавления в него не попадают.
     */
    @Test
    void returnsImmutableSnapshot() {
        storage.add(firstUserId, matrix);
        List<WatchlistEntry> snapshot = storage.getAll(firstUserId);

        WatchlistEntry extra = new WatchlistEntry(avatar, WatchlistEntry.NOT_RATED);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> snapshot.add(extra));

        storage.add(firstUserId, avatar);
        Assertions.assertEquals(unrated(matrix), snapshot);
    }

    /**
     * Существующий фильм удаляется по идентификатору, остальные остаются на своих местах.
     */
    @Test
    void removeExisting() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.add(firstUserId, dune);

        Assertions.assertTrue(storage.remove(firstUserId, avatar.id()));
        Assertions.assertEquals(unrated(matrix, dune), storage.getAll(firstUserId));
    }

    /**
     * Удалённый фильм можно добавить снова, он встаёт в конец списка.
     */
    @Test
    void readdAfterRemove() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.remove(firstUserId, matrix.id());

        Assertions.assertTrue(storage.add(firstUserId, matrix));
        Assertions.assertEquals(unrated(avatar, matrix), storage.getAll(firstUserId));
    }

    /**
     * Удаление несуществующего фильма ничего не меняет.
     */
    @Test
    void removeMissing() {
        storage.add(firstUserId, matrix);

        Assertions.assertFalse(storage.remove(firstUserId, avatar.id()));
        Assertions.assertEquals(unrated(matrix), storage.getAll(firstUserId));
    }

    /**
     * Удаление у неизвестного пользователя не вызывает ошибок.
     */
    @Test
    void removeUnknownUser() {
        Assertions.assertFalse(storage.remove(firstUserId, matrix.id()));
    }

    /**
     * Удаление у одного пользователя не затрагивает тот же фильм у другого.
     */
    @Test
    void removeKeepsOtherUsers() {
        storage.add(firstUserId, matrix);
        storage.add(secondUserId, matrix);

        Assertions.assertTrue(storage.remove(firstUserId, matrix.id()));
        Assertions.assertEquals(List.of(), storage.getAll(firstUserId));
        Assertions.assertEquals(unrated(matrix), storage.getAll(secondUserId));
    }

    /**
     * Очень большие идентификаторы пользователей и фильмов хранятся без потерь.
     */
    @Test
    void largeIds() {
        long bigUserId = 5_000_000_000L;
        Movie bigMovie = new Movie(6_000_000_000L, "Матрица", 1999, 8.5);
        storage.add(bigUserId, bigMovie);

        Assertions.assertEquals(unrated(bigMovie), storage.getAll(bigUserId));
        Assertions.assertEquals(List.of(), storage.getAll(firstUserId));
    }

    /**
     * Название хранится как есть: кавычки, символы SQL и эмодзи не ломают запросы.
     */
    @Test
    void specialCharacters() {
        Movie strange = new Movie(1, "O'Brien; DROP TABLE watchlist_movies; -- 😀 \"кавычки\"", 0, 0);
        storage.add(firstUserId, strange);

        Assertions.assertEquals(unrated(strange), storage.getAll(firstUserId));
        Assertions.assertTrue(storage.remove(firstUserId, strange.id()));
    }

    /**
     * Данные остаются в файле базы и видны при повторном открытии другим экземпляром хранилища.
     */
    @Test
    void survivesReopen() {
        String url = urlOf("reopen.db");
        JdbcWatchlistStorage first = new JdbcWatchlistStorage(url);
        first.add(1L, matrix);
        first.add(1L, avatar);
        first.add(2L, dune);

        JdbcWatchlistStorage second = new JdbcWatchlistStorage(url);

        Assertions.assertEquals(unrated(matrix, avatar), second.getAll(1L));
        Assertions.assertEquals(unrated(dune), second.getAll(2L));
    }

    /**
     * Повторное открытие существующей базы не стирает данные и не падает на уже созданной таблице.
     */
    @Test
    void reopenKeepsData() {
        String url = urlOf("again.db");
        new JdbcWatchlistStorage(url).add(1L, matrix);

        JdbcWatchlistStorage second = new JdbcWatchlistStorage(url);
        second.add(1L, avatar);

        Assertions.assertEquals(unrated(matrix, avatar), second.getAll(1L));
    }

    /**
     * Оценка сохраняется у выбранного фильма и читается вместе с ним; остальные фильмы
     * остаются без оценки и сохраняют порядок.
     */
    @Test
    void rateChosenMovie() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.add(firstUserId, dune);

        Assertions.assertTrue(storage.rate(firstUserId, avatar.id(), 8));

        Assertions.assertEquals(List.of(new WatchlistEntry(matrix, WatchlistEntry.NOT_RATED),
                        new WatchlistEntry(avatar, 8), new WatchlistEntry(dune, WatchlistEntry.NOT_RATED)),
                storage.getAll(firstUserId));
    }

    /**
     * Повторная оценка заменяет прежнюю.
     */
    @Test
    void rateReplacesRating() {
        storage.add(firstUserId, matrix);
        storage.rate(firstUserId, matrix.id(), 4);

        Assertions.assertTrue(storage.rate(firstUserId, matrix.id(), 9));

        Assertions.assertEquals(List.of(new WatchlistEntry(matrix, 9)), storage.getAll(firstUserId));
    }

    /**
     * Фильм без оценки не считается просмотренным, а с оценкой считается.
     */
    @Test
    void watchedStatus() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.rate(firstUserId, avatar.id(), 6);

        List<WatchlistEntry> entries = storage.getAll(firstUserId);

        Assertions.assertFalse(entries.get(0).isWatched());
        Assertions.assertTrue(entries.get(1).isWatched());
    }

    /**
     * Оценить можно только фильм из списка: у неизвестного пользователя и для чужого фильма
     * хранилище отвечает {@code false} и ничего не создаёт.
     */
    @Test
    void rateMissing() {
        storage.add(firstUserId, matrix);

        Assertions.assertFalse(storage.rate(firstUserId, avatar.id(), 7));
        Assertions.assertFalse(storage.rate(secondUserId, matrix.id(), 7));

        Assertions.assertEquals(unrated(matrix), storage.getAll(firstUserId));
        Assertions.assertEquals(List.of(), storage.getAll(secondUserId));
    }

    /**
     * Оценка принадлежит паре «пользователь — фильм»: у второго пользователя тот же фильм
     * остаётся без оценки.
     */
    @Test
    void rateKeepsOtherUsers() {
        storage.add(firstUserId, matrix);
        storage.add(secondUserId, matrix);

        Assertions.assertTrue(storage.rate(firstUserId, matrix.id(), 10));

        Assertions.assertEquals(List.of(new WatchlistEntry(matrix, 10)), storage.getAll(firstUserId));
        Assertions.assertEquals(unrated(matrix), storage.getAll(secondUserId));
    }

    /**
     * Повторное добавление фильма, который уже в списке, не стирает его оценку.
     */
    @Test
    void readdKeepsRating() {
        storage.add(firstUserId, matrix);
        storage.rate(firstUserId, matrix.id(), 9);

        Assertions.assertFalse(storage.add(firstUserId, matrix));

        Assertions.assertEquals(List.of(new WatchlistEntry(matrix, 9)), storage.getAll(firstUserId));
    }

    /**
     * Фильм, удалённый и добавленный снова, возвращается в список без прежней оценки.
     */
    @Test
    void readdAfterRemoveResetsRating() {
        storage.add(firstUserId, matrix);
        storage.rate(firstUserId, matrix.id(), 9);
        storage.remove(firstUserId, matrix.id());

        Assertions.assertTrue(storage.add(firstUserId, matrix));

        Assertions.assertEquals(unrated(matrix), storage.getAll(firstUserId));
    }

    /**
     * Оценки остаются в файле базы и видны после повторного открытия, то есть переживают
     * перезапуск бота.
     */
    @Test
    void ratingsSurviveReopen() {
        String url = urlOf("ratings.db");
        JdbcWatchlistStorage first = new JdbcWatchlistStorage(url);
        first.add(1L, matrix);
        first.add(1L, avatar);
        first.rate(1L, matrix.id(), 9);

        JdbcWatchlistStorage second = new JdbcWatchlistStorage(url);

        Assertions.assertEquals(List.of(new WatchlistEntry(matrix, 9),
                new WatchlistEntry(avatar, WatchlistEntry.NOT_RATED)), second.getAll(1L));
    }

    /**
     * База, созданная до появления оценок, обновляется при открытии: сохранённые фильмы остаются,
     * получают статус «не оценён» и их можно оценить.
     * @throws SQLException если не удалось подготовить старую базу
     */
    @Test
    void upgradesOldDatabase() throws SQLException {
        String url = urlOf("old.db");
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE watchlist_movies ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, "
                    + "movie_id INTEGER NOT NULL, title TEXT NOT NULL, year INTEGER NOT NULL, "
                    + "rating REAL NOT NULL, UNIQUE (user_id, movie_id))");
            statement.executeUpdate("INSERT INTO watchlist_movies (user_id, movie_id, title, year, rating) "
                    + "VALUES (1, 301, 'Матрица', 1999, 8.5)");
        }

        JdbcWatchlistStorage upgraded = new JdbcWatchlistStorage(url);

        Assertions.assertEquals(unrated(matrix), upgraded.getAll(1L));
        Assertions.assertTrue(upgraded.rate(1L, matrix.id(), 9));
        Assertions.assertEquals(List.of(new WatchlistEntry(matrix, 9)), upgraded.getAll(1L));
    }

    /**
     * Ошибка базы данных превращается в {@link IllegalStateException}, а не в проверяемое исключение.
     */
    @Test
    void invalidUrl() {
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> new JdbcWatchlistStorage("jdbc:no-such-database:test"));
        Assertions.assertEquals("Не удалось подготовить базу данных jdbc:no-such-database:test",
                exception.getMessage());
    }

    /**
     * Если файл базы нельзя создать (нет такой папки), хранилище сообщает об этом
     * {@link IllegalStateException}.
     */
    @Test
    void unavailableFile() {
        String url = urlOf("no-such-directory/watchlist.db");

        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> new JdbcWatchlistStorage(url));
        Assertions.assertEquals("Не удалось подготовить базу данных " + url, exception.getMessage());
    }

    /**
     * Превращает фильмы в записи списка без оценки, в том же порядке.
     * @param movies фильмы
     * @return записи с оценкой {@link WatchlistEntry#NOT_RATED}
     */
    private List<WatchlistEntry> unrated(Movie... movies) {
        List<WatchlistEntry> entries = new ArrayList<>();
        for (Movie movie : movies) {
            entries.add(new WatchlistEntry(movie, WatchlistEntry.NOT_RATED));
        }
        return entries;
    }

    /**
     * Строит адрес базы SQLite для файла во временной папке.
     * @param fileName имя или относительный путь файла внутри временной папки
     * @return адрес в формате JDBC
     */
    private String urlOf(String fileName) {
        return "jdbc:sqlite:" + tempDirectory.resolve(fileName);
    }
}