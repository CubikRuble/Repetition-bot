package org.example.catalog;

import java.io.IOException;
import java.util.List;

/**
 * Каталог фильмов, в котором можно искать по названию. Интерфейс ничего не знает ни о Telegram,
 * ни о логике диалога, ни о конкретном сайте, поэтому в тестах Кинопоиск заменяется
 * заготовленными данными.
 */
public interface MovieCatalog {

    /**
     * Ищет фильмы по названию.
     * @param title название или его часть
     * @param limit максимальное число возвращаемых фильмов
     * @return найденные фильмы в порядке релевантности; пустой список, если ничего не найдено
     * @throws IOException если каталог недоступен или вернул непонятный ответ
     */
    List<Movie> search(String title, int limit) throws IOException;
}
