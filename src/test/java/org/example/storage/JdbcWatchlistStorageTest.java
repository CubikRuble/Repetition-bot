package org.example.storage;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Тесты хранилища {@link JdbcWatchlistStorage} на SQLite.
 */
class JdbcWatchlistStorageTest {

    /** Адрес базы SQLite в памяти. */
    private static final String IN_MEMORY_URL = "jdbc:sqlite::memory:";

    /** Фильмы возвращаются в порядке добавления, у каждого пользователя свой список. */
    @Test
    void keepsOrderPerUser() {
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage(IN_MEMORY_URL)) {
            storage.add(1L, "Матрица");
            storage.add(1L, "Аватар");
            storage.add(5_000_000_000L, "Дюна");
            Assertions.assertEquals(List.of("Матрица", "Аватар"), storage.getAll(1L));
            Assertions.assertEquals(List.of("Дюна"), storage.getAll(5_000_000_000L));
            Assertions.assertEquals(List.of(), storage.getAll(2L));
        }
    }

    /** Метод remove удаляет только нужный фильм у нужного пользователя и сообщает, было ли удаление. */
    @Test
    void removesOneTitle() {
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage(IN_MEMORY_URL)) {
            storage.add(1L, "Матрица");
            storage.add(1L, "Аватар");
            storage.add(2L, "Матрица");
            Assertions.assertTrue(storage.remove(1L, "Матрица"));
            Assertions.assertFalse(storage.remove(1L, "матрица"));
            Assertions.assertEquals(List.of("Аватар"), storage.getAll(1L));
            Assertions.assertEquals(List.of("Матрица"), storage.getAll(2L));
        }
    }

    /** Названия с кавычками и SQL-кодом сохраняются как обычный текст. */
    @Test
    void savesSpecialCharacters() {
        String title = "O'Brien; DROP TABLE watchlist; -- \"кавычки\"";
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage(IN_MEMORY_URL)) {
            storage.add(1L, title);
            Assertions.assertEquals(List.of(title), storage.getAll(1L));
        }
    }

    /**
     * Данные сохраняются в файле и доступны после повторного открытия базы.
     * @param directory временная директория для файла базы
     */
    @Test
    void survivesRestart(@TempDir Path directory) {
        String url = "jdbc:sqlite:" + directory.resolve("watchlist.db");
        try (JdbcWatchlistStorage first = new JdbcWatchlistStorage(url)) {
            first.add(1L, "Матрица");
        }
        try (JdbcWatchlistStorage second = new JdbcWatchlistStorage(url)) {
            Assertions.assertEquals(List.of("Матрица"), second.getAll(1L));
        }
    }

    /** Неверный адрес базы даёт понятное исключение. */
    @Test
    void rejectsBadUrl() {
        Assertions.assertThrows(IllegalStateException.class, () -> new JdbcWatchlistStorage("jdbc:no-such-database:test"));
    }
}