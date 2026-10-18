package org.example.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
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
    void keepsOrderAndSeparatesUsers() {
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage(IN_MEMORY_URL)) {
            storage.add(1L, "Матрица");
            storage.add(1L, "Аватар");
            storage.add(5_000_000_000L, "Дюна");

            assertEquals(List.of("Матрица", "Аватар"), storage.getAll(1L));
            assertEquals(List.of("Дюна"), storage.getAll(5_000_000_000L));
            assertEquals(List.of(), storage.getAll(2L));
        }
    }

    /** remove удаляет только нужный фильм у нужного пользователя и сообщает, было ли удаление. */
    @Test
    void removeDeletesOnlyMatchingTitleOfUser() {
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage(IN_MEMORY_URL)) {
            storage.add(1L, "Матрица");
            storage.add(1L, "Аватар");
            storage.add(2L, "Матрица");

            assertTrue(storage.remove(1L, "Матрица"));
            assertFalse(storage.remove(1L, "матрица"));
            assertEquals(List.of("Аватар"), storage.getAll(1L));
            assertEquals(List.of("Матрица"), storage.getAll(2L));
        }
    }

    /** Названия с кавычками и SQL-кодом сохраняются как обычный текст. */
    @Test
    void specialCharactersAreStoredAsText() {
        String title = "O'Brien; DROP TABLE watchlist; -- \"кавычки\"";
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage(IN_MEMORY_URL)) {
            storage.add(1L, title);

            assertEquals(List.of(title), storage.getAll(1L));
        }
    }

    /** Данные сохраняются в файле и доступны после повторного открытия базы. */
    @Test
    void dataSurvivesReopening(@TempDir Path directory) {
        String url = "jdbc:sqlite:" + directory.resolve("watchlist.db");
        try (JdbcWatchlistStorage first = new JdbcWatchlistStorage(url)) {
            first.add(1L, "Матрица");
        }
        try (JdbcWatchlistStorage second = new JdbcWatchlistStorage(url)) {
            assertEquals(List.of("Матрица"), second.getAll(1L));
        }
    }

    /** Неверный адрес базы даёт понятное исключение. */
    @Test
    void invalidUrlThrowsException() {
        assertThrows(IllegalStateException.class, () -> new JdbcWatchlistStorage("jdbc:no-such-database:test"));
    }
}