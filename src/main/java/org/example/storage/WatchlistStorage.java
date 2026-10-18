package org.example.storage;

import java.util.List;

/**
 * Хранилище списков фильмов «посмотреть позже». У каждого пользователя свой список;
 * порядок добавления сохраняется. Не зависит от логики диалога.
 */
public interface WatchlistStorage {

    /**
     * Возвращает все фильмы пользователя в порядке добавления.
     *
     * @param userId идентификатор пользователя
     * @return неизменяемая копия списка (пустая, если фильмов нет)
     */
    List<String> getAll(long userId);

    /**
     * Добавляет фильм в конец списка пользователя.
     *
     * @param userId идентификатор пользователя
     * @param title  название фильма
     */
    void add(long userId, String title);

    /**
     * Удаляет фильм из списка пользователя (с учётом регистра).
     *
     * @param userId идентификатор пользователя
     * @param title  название фильма
     * @return {@code true}, если фильм был в списке и удалён
     */
    boolean remove(long userId, String title);
}