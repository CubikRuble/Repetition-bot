package org.example.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdbcWatchlistStorageTest {

    @Test
    void addGetAndRemove() {
        try (JdbcWatchlistStorage storage = new JdbcWatchlistStorage("jdbc:sqlite::memory:")) {

            storage.add(1, "Матрица");

            assertEquals(java.util.List.of("Матрица"), storage.getAll(1));

            assertTrue(storage.remove(1, "Матрица"));
            assertTrue(storage.getAll(1).isEmpty());
        }
    }
}