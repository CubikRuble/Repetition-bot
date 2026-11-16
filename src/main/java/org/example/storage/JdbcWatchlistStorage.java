package org.example.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.example.catalog.Movie;

/**
 * Реализация хранилища на JDBC: списки пользователей лежат в таблице базы данных
 * (фильм сохраняется целиком, вместе с идентификатором Кинопоиска и оценкой пользователя)
 * и не пропадают после перезапуска бота. Рассчитана на SQLite, но использует только
 * стандартный SQL. Класс сам управляет соединениями: каждая операция открывает своё соединение
 * и закрывает его до возврата, поэтому снаружи нечего закрывать, а общего состояния,
 * которое нужно защищать блокировками, нет.
 */
public class JdbcWatchlistStorage implements WatchlistStorage {

    /** Название таблицы со списками фильмов. */
    private static final String TABLE_NAME = "watchlist_movies";

    /** Название столбца с оценкой пользователя. */
    private static final String RATING_COLUMN = "user_rating";

    /**
     * Запрос, создающий таблицу фильмов, если её ещё нет. Порядок добавления задаёт столбец id,
     * а пара (user_id, movie_id) уникальна: один фильм Кинопоиска не попадает в список дважды.
     * Столбец user_rating хранит оценку пользователя, 0 означает «не оценён».
     */
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS watchlist_movies ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, movie_id INTEGER NOT NULL, "
            + "title TEXT NOT NULL, year INTEGER NOT NULL, rating REAL NOT NULL, "
            + "user_rating INTEGER NOT NULL DEFAULT 0, UNIQUE (user_id, movie_id))";

    /**
     * Запрос, добавляющий столбец оценки в таблицу, созданную до появления оценок. У уже
     * сохранённых фильмов оценка становится равной 0, то есть «не оценён».
     */
    private static final String ADD_RATING_COLUMN_SQL = "ALTER TABLE watchlist_movies ADD COLUMN user_rating INTEGER NOT NULL DEFAULT 0";

    /** Запрос на получение фильмов пользователя вместе с оценками в порядке добавления. */
    private static final String SELECT_SQL = "SELECT movie_id, title, year, rating, user_rating FROM watchlist_movies WHERE user_id = ? ORDER BY id";

    /** Запрос на сохранение оценки фильма пользователя; прежняя оценка заменяется. */
    private static final String RATE_SQL = "UPDATE watchlist_movies SET user_rating = ? WHERE user_id = ? AND movie_id = ?";

    /** Запрос на добавление фильма; повторное добавление того же фильма игнорируется. */
    private static final String INSERT_SQL = "INSERT INTO watchlist_movies (user_id, movie_id, title, year, rating) VALUES (?, ?, ?, ?, ?) ON CONFLICT (user_id, movie_id) DO NOTHING";

    /** Запрос на удаление фильма пользователя по идентификатору Кинопоиска. */
    private static final String DELETE_SQL = "DELETE FROM watchlist_movies WHERE user_id = ? AND movie_id = ?";

    /** Адрес базы данных в формате JDBC. */
    private final String jdbcUrl;

    /**
     * Запоминает адрес базы данных, создаёт таблицу, если её ещё нет, и добавляет в таблицу,
     * созданную прежней версией бота, столбец оценки: сохранённые списки при этом не теряются.
     * @param jdbcUrl адрес базы данных, например {@code jdbc:sqlite:watchlist.db}; для базы в
     *                оперативной памяти не подходит, так как у каждого соединения она была бы своя
     * @throws IllegalStateException если базу не удалось открыть или подготовить
     */
    public JdbcWatchlistStorage(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE_SQL);
            if (!hasRatingColumn(connection)) {
                statement.executeUpdate(ADD_RATING_COLUMN_SQL);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось подготовить базу данных " + jdbcUrl, e);
        }
    }

    /**
     * Возвращает неизменяемый список записей пользователя в порядке добавления.
     * @param userId идентификатор пользователя
     * @return фильмы пользователя с оценками; пустой список, если у пользователя нет фильмов
     * @throws IllegalStateException если запрос к базе не удался
     */
    @Override
    public List<WatchlistEntry> getAll(long userId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<WatchlistEntry> entries = new ArrayList<>();
                while (rows.next()) {
                    Movie movie = new Movie(rows.getLong("movie_id"), rows.getString("title"),
                            rows.getInt("year"), rows.getDouble("rating"));
                    entries.add(new WatchlistEntry(movie, rows.getInt(RATING_COLUMN)));
                }
                return List.copyOf(entries);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось прочитать список пользователя " + userId, e);
        }
    }

    /**
     * Добавляет фильм в конец списка пользователя, если его там ещё нет.
     * @param userId идентификатор пользователя
     * @param movie фильм, который сохраняется целиком
     * @return {@code true}, если фильм добавлен; {@code false}, если он уже был в списке
     * @throws IllegalStateException если запись в базу не удалась
     */
    @Override
    public boolean add(long userId, Movie movie) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setLong(1, userId);
            statement.setLong(2, movie.id());
            statement.setString(3, movie.title());
            statement.setInt(4, movie.year());
            statement.setDouble(5, movie.rating());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось добавить фильм пользователю " + userId, e);
        }
    }

    /**
     * Сохраняет оценку фильма из списка пользователя; прежняя оценка заменяется.
     * @param userId идентификатор пользователя
     * @param movieId идентификатор фильма на Кинопоиске
     * @param rating оценка пользователя от 1 до 10
     * @return {@code true}, если фильм был в списке и оценка сохранена
     * @throws IllegalStateException если запись в базу не удалась
     */
    @Override
    public boolean rate(long userId, long movieId, int rating) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(RATE_SQL)) {
            statement.setInt(1, rating);
            statement.setLong(2, userId);
            statement.setLong(3, movieId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось сохранить оценку пользователя " + userId, e);
        }
    }

    /**
     * Удаляет фильм из списка пользователя.
     * @param userId идентификатор пользователя
     * @param movieId идентификатор фильма на Кинопоиске
     * @return {@code true}, если фильм был найден и удалён
     * @throws IllegalStateException если запрос к базе не удался
     */
    @Override
    public boolean remove(long userId, long movieId) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setLong(1, userId);
            statement.setLong(2, movieId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось удалить фильм у пользователя " + userId, e);
        }
    }

    /**
     * Проверяет, есть ли в таблице фильмов столбец оценки. Использует стандартные метаданные JDBC,
     * поэтому не зависит от конкретной базы данных.
     * @param connection открытое соединение с базой
     * @return {@code true}, если столбец оценки уже создан
     * @throws SQLException если метаданные прочитать не удалось
     */
    private boolean hasRatingColumn(Connection connection) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, TABLE_NAME, RATING_COLUMN)) {
            return columns.next();
        }
    }

    /**
     * Открывает новое соединение с базой данных. Закрывает его тот, кто вызвал метод.
     * @return открытое соединение
     * @throws SQLException если соединение открыть не удалось
     */
    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
