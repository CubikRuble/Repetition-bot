package org.example.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Хранилище на JDBC и SQLite: данные сохраняются между запусками программы.
 * Все операции с единственным соединением синхронизированы.
 */
public class JdbcWatchlistStorage implements WatchlistStorage, AutoCloseable {

    /** Создание таблицы списков; порядок строк (id) задаёт порядок добавления. */
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS watchlist "
            + "(id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, title TEXT NOT NULL)";

    /** Выбор всех фильмов пользователя в порядке добавления. */
    private static final String SELECT_SQL = "SELECT title FROM watchlist WHERE user_id = ? ORDER BY id";

    /** Добавление фильма пользователю. */
    private static final String INSERT_SQL = "INSERT INTO watchlist (user_id, title) VALUES (?, ?)";

    /** Удаление самой ранней записи с таким названием у пользователя. */
    private static final String DELETE_SQL = "DELETE FROM watchlist WHERE id = "
            + "(SELECT MIN(id) FROM watchlist WHERE user_id = ? AND title = ?)";

    /** Соединение с базой данных. */
    private final Connection connection;

    /**
     * Открывает базу данных и создаёт таблицу, если её ещё нет.
     *
     * @param jdbcUrl JDBC-адрес базы, например {@code jdbc:sqlite:watchlist.db}
     * @throws IllegalStateException если базу не удалось открыть
     */
    public JdbcWatchlistStorage(String jdbcUrl) {
        try {
            connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_TABLE_SQL);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось открыть базу данных " + jdbcUrl, e);
        }
    }

    @Override
    public synchronized List<String> getAll(long userId) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                List<String> titles = new ArrayList<>();
                while (rows.next()) {
                    titles.add(rows.getString("title"));
                }
                return List.copyOf(titles);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось прочитать список пользователя " + userId, e);
        }
    }

    @Override
    public synchronized void add(long userId, String title) {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setLong(1, userId);
            statement.setString(2, title);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось добавить фильм пользователю " + userId, e);
        }
    }

    @Override
    public synchronized boolean remove(long userId, String title) {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setLong(1, userId);
            statement.setString(2, title);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось удалить фильм у пользователя " + userId, e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось закрыть базу данных", e);
        }
    }
}