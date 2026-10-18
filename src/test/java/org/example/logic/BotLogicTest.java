package org.example.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotLogicTest {

    @Test
    void addListAndRemoveMovie() {
        BotLogic logic = new BotLogic(new InMemoryWatchlistStorage());
        assertEquals("Фильм добавлен", logic.handleMessage(1, "/add Матрица"));
        assertEquals("1. Матрица", logic.handleMessage(1, "/list"));
        assertEquals("Фильм удалён", logic.handleMessage(1, "/remove Матрица"));
    }

    @Test
    void usersHaveSeparateLists() {
        BotLogic logic = new BotLogic(new InMemoryWatchlistStorage());
        logic.handleMessage(1, "/add Матрица");
        assertEquals("Список пока пуст. Добавь фильм командой /add <название>", logic.handleMessage(2, "/list")
        );
    }
}