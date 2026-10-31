package org.example.storage;

import java.nio.file.Path;
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
    void emptyListForUnknownUser() {
        Assertions.assertEquals(List.of(), storage.getAll(firstUserId));
    }

    /**
     * Фильм возвращается целиком: идентификатор, название, год и рейтинг сохраняются без потерь.
     */
    @Test
    void movieAllFields() {
        Assertions.assertTrue(storage.add(firstUserId, matrix));
        Assertions.assertEquals(List.of(matrix), storage.getAll(firstUserId));
    }

    /**
     * Фильмы возвращаются в том порядке, в котором их добавили.
     */
    @Test
    void correctOrder() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.add(firstUserId, dune);
        Assertions.assertEquals(List.of(matrix, avatar, dune), storage.getAll(firstUserId));
    }

    /**
     * Один и тот же фильм нельзя добавить пользователю дважды.
     */
    @Test
    void notAddedTwice() {
        Assertions.assertTrue(storage.add(firstUserId, matrix));
        Assertions.assertFalse(storage.add(firstUserId, matrix));
        Assertions.assertEquals(List.of(matrix), storage.getAll(firstUserId));
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
        Assertions.assertEquals(List.of(islandOld, islandNew), storage.getAll(firstUserId));
    }

    /**
     * Списки разных пользователей не пересекаются, а один фильм может быть у обоих.
     */
    @Test
    void listsOfDifferentUsers() {
        storage.add(firstUserId, matrix);
        storage.add(secondUserId, avatar);

        Assertions.assertTrue(storage.add(secondUserId, matrix));
        Assertions.assertEquals(List.of(matrix), storage.getAll(firstUserId));
        Assertions.assertEquals(List.of(avatar, matrix), storage.getAll(secondUserId));
    }

    /**
     * Возвращённый список нельзя изменить, а последующие добавления в него не попадают.
     */
    @Test
    void getAllIsImmutableSnapshot() {
        storage.add(firstUserId, matrix);
        List<Movie> snapshot = storage.getAll(firstUserId);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> snapshot.add(avatar));
        storage.add(firstUserId, avatar);
        Assertions.assertEquals(List.of(matrix), snapshot);
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
        Assertions.assertEquals(List.of(matrix, dune), storage.getAll(firstUserId));
    }

    /**
     * Удалённый фильм можно добавить снова, он встаёт в конец списка.
     */
    @Test
    void addAfterRemove() {
        storage.add(firstUserId, matrix);
        storage.add(firstUserId, avatar);
        storage.remove(firstUserId, matrix.id());

        Assertions.assertTrue(storage.add(firstUserId, matrix));
        Assertions.assertEquals(List.of(avatar, matrix), storage.getAll(firstUserId));
    }

    /**
     * Удаление несуществующего фильма ничего не меняет.
     */
    @Test
    void removeMissing() {
        storage.add(firstUserId, matrix);

        Assertions.assertFalse(storage.remove(firstUserId, avatar.id()));
        Assertions.assertEquals(List.of(matrix), storage.getAll(firstUserId));
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
    void removeIsolatedByUser() {
        storage.add(firstUserId, matrix);
        storage.add(secondUserId, matrix);

        Assertions.assertTrue(storage.remove(firstUserId, matrix.id()));
        Assertions.assertEquals(List.of(), storage.getAll(firstUserId));
        Assertions.assertEquals(List.of(matrix), storage.getAll(secondUserId));
    }

    /**
     * Очень большие идентификаторы пользователей и фильмов хранятся без потерь.
     */
    @Test
    void largeIds() {
        long bigUserId = 5_000_000_000L;
        Movie bigMovie = new Movie(6_000_000_000L, "Матрица", 1999, 8.5);
        storage.add(bigUserId, bigMovie);
        Assertions.assertEquals(List.of(bigMovie), storage.getAll(bigUserId));
        Assertions.assertEquals(List.of(), storage.getAll(firstUserId));
    }

    /**
     * Название хранится как есть: кавычки, символы SQL и эмодзи не ломают запросы.
     */
    @Test
    void specialCharacters() {
        Movie strange = new Movie(1, "O'Brien; DROP TABLE watchlist_movies; -- 😀 \"кавычки\"", 0, 0);
        storage.add(firstUserId, strange);
        Assertions.assertEquals(List.of(strange), storage.getAll(firstUserId));
        Assertions.assertTrue(storage.remove(firstUserId, strange.id()));
    }

    /**
     * Данные остаются в файле базы и видны при повторном открытии другим экземпляром хранилища.
     */
    @Test
    void dataPersists() {
        String url = urlOf("reopen.db");
        JdbcWatchlistStorage first = new JdbcWatchlistStorage(url);
        first.add(1L, matrix);
        first.add(1L, avatar);
        first.add(2L, dune);

        JdbcWatchlistStorage second = new JdbcWatchlistStorage(url);

        Assertions.assertEquals(List.of(matrix, avatar), second.getAll(1L));
        Assertions.assertEquals(List.of(dune), second.getAll(2L));
    }

    /**
     * Повторное открытие существующей базы не стирает данные и не падает на уже созданной таблице.
     */
    @Test
    void reopenKeepsTable() {
        String url = urlOf("again.db");
        new JdbcWatchlistStorage(url).add(1L, matrix);

        JdbcWatchlistStorage second = new JdbcWatchlistStorage(url);
        second.add(1L, avatar);
        Assertions.assertEquals(List.of(matrix, avatar), second.getAll(1L));
    }

    /**
     * Ошибка базы данных превращается в {@link IllegalStateException}, а не в проверяемое исключение.
     */
    @Test
    void invalidUrl() {
        Assertions.assertThrows(IllegalStateException.class,
                () -> new JdbcWatchlistStorage("jdbc:no-such-database:test"));
    }

    /**
     * Если файл базы нельзя создать (нет такой папки), хранилище сообщает об этом
     * {@link IllegalStateException}.
     */
    @Test
    void unavailableFile() {
        String url = urlOf("no-such-directory/watchlist.db");

        Assertions.assertThrows(IllegalStateException.class, () -> new JdbcWatchlistStorage(url));
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
