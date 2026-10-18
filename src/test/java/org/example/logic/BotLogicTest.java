package org.example.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.example.storage.JdbcWatchlistStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Функциональные тесты диалога: логика {@link BotLogic} вместе с SQLite в памяти.
 */
class BotLogicTest {

    /** Идентификатор первого пользователя. */
    private static final long FIRST_USER = 1L;

    /** Идентификатор второго пользователя. */
    private static final long SECOND_USER = 2L;

    /** Хранилище в памяти, создаётся заново для каждого теста. */
    private JdbcWatchlistStorage storage;

    /** Тестируемая логика диалога. */
    private BotLogic logic;

    /** Создаёт чистое хранилище и логику перед каждым тестом. */
    @BeforeEach
    void setUp() {
        storage = new JdbcWatchlistStorage("jdbc:sqlite::memory:");
        logic = new BotLogic(storage);
    }

    /** Закрывает хранилище после теста. */
    @AfterEach
    void tearDown() {
        storage.close();
    }

    /** Добавление, просмотр и удаление фильмов; регистр и лишние пробелы не важны. */
    @Test
    void addListRemoveFlow() {
        assertEquals("Фильм добавлен", logic.handleMessage(FIRST_USER, "/add   Матрица:   Перезагрузка "));
        assertEquals("Фильм добавлен", logic.handleMessage(FIRST_USER, "/ADD Дюна"));
        assertEquals("1. Матрица: Перезагрузка\n2. Дюна", logic.handleMessage(FIRST_USER, "/list"));

        assertEquals("Фильм удалён", logic.handleMessage(FIRST_USER, "/remove матрица: ПЕРЕЗАГРУЗКА"));
        assertEquals("1. Дюна", logic.handleMessage(FIRST_USER, "/list"));
    }

    /** Ошибки: повторное добавление, удаление отсутствующего фильма, команда без названия. */
    @Test
    void errorsFlow() {
        logic.handleMessage(FIRST_USER, "/add Матрица");

        assertEquals("Этот фильм уже есть в списке", logic.handleMessage(FIRST_USER, "/add МАТРИЦА"));
        assertEquals("Такого фильма нет в списке", logic.handleMessage(FIRST_USER, "/remove Аватар"));
        assertEquals("Укажите название: /add <название>", logic.handleMessage(FIRST_USER, "/add"));
        assertEquals("Укажите название: /remove <название>", logic.handleMessage(FIRST_USER, "/remove   "));
        assertEquals("1. Матрица", logic.handleMessage(FIRST_USER, "/list"));
    }

    /** У двух пользователей независимые списки, даже с одинаковыми фильмами. */
    @Test
    void twoUsersHaveSeparateLists() {
        logic.handleMessage(FIRST_USER, "/add Матрица");
        String emptyList = "Список пока пуст. Добавь фильм командой /add <название>";
        assertEquals(emptyList, logic.handleMessage(SECOND_USER, "/list"));

        assertEquals("Фильм добавлен", logic.handleMessage(SECOND_USER, "/add Матрица"));
        logic.handleMessage(FIRST_USER, "/remove Матрица");
        assertEquals(emptyList, logic.handleMessage(FIRST_USER, "/list"));
        assertEquals("1. Матрица", logic.handleMessage(SECOND_USER, "/list"));
    }

    /** /start и /help показывают справку; пустой и неизвестный ввод получают подсказку. */
    @Test
    void helpBlankAndUnknownInput() {
        String help = logic.handleMessage(FIRST_USER, "/start");
        assertEquals(help, logic.handleMessage(FIRST_USER, " /HELP "));
        assertEquals("Вы отправили пустое сообщение.", logic.handleMessage(FIRST_USER, "  "));
        for (String input : Arrays.asList(null, "", "\t")) {
            assertEquals("Вы отправили пустое сообщение.", logic.handleMessage(FIRST_USER, input));
        }
        for (String input : Arrays.asList("привет", "/unknown", "add Матрица")) {
            assertEquals("Не понял вас. Список команд: /help", logic.handleMessage(FIRST_USER, input));
        }
    }
}