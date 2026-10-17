package org.example.storage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcWatchlistStorage
        implements WatchlistStorage, AutoCloseable {

    private final Connection connection;

    public JdbcWatchlistStorage(String url) {
        try {
            connection = DriverManager.getConnection(url);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS watchlist (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            user_id INTEGER NOT NULL,
                            title TEXT NOT NULL
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new StorageException("Не удалось открыть базу данных", e);
        }
    }

    @Override
    public synchronized List<String> getAll(long userId) {
        String sql = """
                SELECT title FROM watchlist
                WHERE user_id = ?
                ORDER BY id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet rows = statement.executeQuery()) {
                List<String> titles = new ArrayList<>();

                while (rows.next()) {
                    titles.add(rows.getString("title"));
                }

                return List.copyOf(titles);
            }
        } catch (SQLException e) {
            throw new StorageException("Ошибка чтения списка", e);
        }
    }

    @Override
    public synchronized void add(long userId, String title) {
        String sql = """
                INSERT INTO watchlist (user_id, title)
                VALUES (?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);
            statement.setString(2, title);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Ошибка добавления фильма", e);
        }
    }

    @Override
    public synchronized boolean remove(long userId, String title) {
        String sql = """
                DELETE FROM watchlist
                WHERE id = (
                    SELECT MIN(id) FROM watchlist
                    WHERE user_id = ? AND title = ?
                )
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, userId);
            statement.setString(2, title);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new StorageException("Ошибка удаления фильма", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new StorageException("Ошибка закрытия базы", e);
        }
    }
}