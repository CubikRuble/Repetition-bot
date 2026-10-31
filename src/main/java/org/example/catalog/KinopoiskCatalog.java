package org.example.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Каталог фильмов на основе Кинопоиска (неофициальный API kinopoisk.dev): отправляет запрос
 * поиска и разбирает JSON-ответ в список {@link Movie}.
 */
public class KinopoiskCatalog implements MovieCatalog {

    /** Адрес поиска фильмов по названию. */
    private static final String SEARCH_URL = "https://api.kinopoisk.dev/v1.4/movie/search";

    /** Название заголовка, в котором передаётся токен доступа. */
    private static final String TOKEN_HEADER = "KINOPOISK_API_TOKEN";

    /** Максимальное время ожидания ответа сайта в секундах. */
    private final int timeoutSeconds = 10;

    /** Код успешного ответа HTTP. */
    private final int httpOk = 200;

    /** Токен доступа к API. */
    private final String apiToken;

    /** HTTP-клиент, через который отправляются запросы. */
    private final HttpClient httpClient;

    /**
     * Создаёт каталог с указанным токеном доступа.
     * @param apiToken токен API kinopoisk.dev
     */
    public KinopoiskCatalog(String apiToken) {
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /**
     * Ищет фильмы на Кинопоиске по названию.
     * @param title название или его часть
     * @param limit максимальное число возвращаемых фильмов
     * @return найденные фильмы в порядке релевантности
     * @throws IOException если нет связи, сайт вернул ошибку или некорректный ответ
     */
    @Override
    public List<Movie> search(String title, int limit) throws IOException {
        String url = SEARCH_URL + "?page=1&limit=" + limit
                + "&query=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header(TOKEN_HEADER, apiToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != httpOk) {
                throw new IOException("Кинопоиск ответил с кодом " + response.statusCode());
            }
            return parse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запрос к Кинопоиску прерван", e);
        }
    }

    /**
     * Разбирает JSON-ответ поиска. Записи без идентификатора или названия пропускаются,
     * отсутствующие год и рейтинг заменяются нулём.
     * @param json тело ответа в формате JSON
     * @return фильмы в том порядке, в котором их вернул Кинопоиск
     * @throws IOException если ответ не является JSON-объектом с массивом {@code docs}
     */
    List<Movie> parse(String json) throws IOException {
        try {
            JsonElement docs = JsonParser.parseString(json).getAsJsonObject().get("docs");
            if (docs == null || !docs.isJsonArray()) {
                throw new IOException("В ответе Кинопоиска нет списка фильмов");
            }
            List<Movie> movies = new ArrayList<>();
            for (JsonElement element : docs.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject doc = element.getAsJsonObject();
                if (isMissing(doc.get("id")) || isMissing(doc.get("name"))) {
                    continue;
                }
                movies.add(new Movie(doc.get("id").getAsLong(), doc.get("name").getAsString(),
                        readYear(doc), readRating(doc)));
            }
            return movies;
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            throw new IOException("Не удалось разобрать ответ Кинопоиска", e);
        }
    }

    /**
     * Читает год выхода фильма.
     * @param doc описание фильма
     * @return год или {@code 0}, если он не указан
     */
    private int readYear(JsonObject doc) {
        JsonElement year = doc.get("year");
        return isMissing(year) ? 0 : year.getAsInt();
    }

    /**
     * Читает рейтинг Кинопоиска ({@code rating.kp}).
     * @param doc описание фильма
     * @return рейтинг или {@code 0}, если он не указан
     */
    private double readRating(JsonObject doc) {
        JsonElement ratings = doc.get("rating");
        if (isMissing(ratings) || !ratings.isJsonObject()) {
            return 0;
        }
        JsonElement kp = ratings.getAsJsonObject().get("kp");
        return isMissing(kp) ? 0 : kp.getAsDouble();
    }

    /**
     * Проверяет, что поля нет в ответе или оно равно {@code null}.
     * @param element значение поля, может быть {@code null}
     * @return {@code true}, если у поля нет значения
     */
    private boolean isMissing(JsonElement element) {
        return element == null || element.isJsonNull();
    }
}
