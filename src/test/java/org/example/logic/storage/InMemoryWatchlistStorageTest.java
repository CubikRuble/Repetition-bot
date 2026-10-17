package org.example.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryWatchlistStorageTest {

    @Test
    void addGetAndRemove() {
        WatchlistStorage storage = new InMemoryWatchlistStorage();

        storage.add(1, "Матрица");

        assertEquals(java.util.List.of("Матрица"), storage.getAll(1));

        assertTrue(storage.remove(1, "Матрица"));
        assertTrue(storage.getAll(1).isEmpty());
    }
}