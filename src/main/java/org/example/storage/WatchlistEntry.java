package org.example.storage;

import org.example.catalog.Movie;

/**
 * Запись в списке «посмотреть позже»: фильм и личная оценка пользователя. Данные Кинопоиска
 * ({@link Movie}) и мнение пользователя хранятся раздельно: оценка принадлежит не фильму,
 * а паре «пользователь — фильм». Фильм с оценкой считается просмотренным.
 * @param movie фильм из каталога
 * @param userRating оценка пользователя от 1 до 10; {@link #NOT_RATED}, если фильм ещё не оценён
 */
public record WatchlistEntry(Movie movie, int userRating) {

    /** Значение оценки у фильма, который пользователь ещё не смотрел и не оценивал. */
    public static final int NOT_RATED = 0;

    /**
     * Показывает, что пользователь уже посмотрел фильм, то есть поставил ему оценку.
     * @return {@code true}, если у фильма есть оценка
     */
    public boolean isWatched() {
        return userRating != NOT_RATED;
    }
}
