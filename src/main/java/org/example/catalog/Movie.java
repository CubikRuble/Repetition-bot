package org.example.catalog;

/**
 * Фильм: неизменяемый набор данных, общий для каталога, логики и хранилища. Фильм однозначно определяется идентификатором Кинопоиска.
 * @param id уникальный идентификатор фильма на Кинопоиске
 * @param title название фильма
 * @param year год выхода; {@code 0}, если каталог его не сообщил
 * @param rating рейтинг фильма; {@code 0}, если рейтинга нет
 */
public record Movie(long id, String title, int year, double rating) {
}