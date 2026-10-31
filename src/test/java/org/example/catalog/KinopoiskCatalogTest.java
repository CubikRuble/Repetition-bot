package org.example.catalog;


import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Тесты разбора ответов Кинопоиска в {@link KinopoiskCatalog} на заранее подготовленных
 * JSON-строках. Интернет не нужен.
 */
class KinopoiskCatalogTest {

    /** Каталог, у которого проверяется разбор ответов; токен не используется. */
    private final KinopoiskCatalog catalog = new KinopoiskCatalog("test-token");

    /**
     * Обычный ответ разбирается: название, год и рейтинг Кинопоиска попадают в {@link Movie},
     * порядок сохраняется, лишние поля игнорируются.
     * @throws IOException если разбор неожиданно не удался
     */
    @Test
    void parsesResponse() throws IOException {
        String json = "{\"docs\":[{\"id\":301,\"name\":\"Матрица\",\"year\":1999,"
                + "\"rating\":{\"kp\":8.5,\"imdb\":8.7}},"
                + "{\"id\":302,\"name\":\"Матрица: Перезагрузка\",\"year\":2003,\"rating\":{\"kp\":7.2}}],"
                + "\"total\":2,\"page\":1}";

        Assertions.assertEquals(List.of(new Movie(301, "Матрица", 1999, 8.5),
                new Movie(302, "Матрица: Перезагрузка", 2003, 7.2)), catalog.parse(json));
    }

    /**
     * Отсутствующие год и рейтинг заменяются нулём, записи без id или названия пропускаются.
     * @throws IOException если разбор неожиданно не удался
     */
    @Test
    void missingFields() throws IOException {
        String json = "{\"docs\":[{\"id\":1,\"name\":\"Без данных\"},"
                + "{\"id\":2,\"name\":\"С пустыми\",\"year\":null,\"rating\":{\"kp\":null}},"
                + "{\"name\":\"Без id\"},{\"id\":3,\"name\":null},\"строка\"]}";

        Assertions.assertEquals(List.of(new Movie(1, "Без данных", 0, 0), new Movie(2, "С пустыми", 0, 0)),
                catalog.parse(json));
        Assertions.assertTrue(catalog.parse("{\"docs\":[]}").isEmpty());
    }

    /**
     * Ответ без списка фильмов или не в формате JSON считается сбоем каталога.
     */
    @Test
    void invalidResponse() {
        String[] inputs = {"", "<html>502 Bad Gateway</html>", "[]", "{\"message\":\"Unauthorized\"}",
                "{\"docs\":\"не массив\"}", "{\"docs\":[}"};
        for (String input : inputs) {
            Assertions.assertThrows(IOException.class, () -> catalog.parse(input));
        }
    }
}