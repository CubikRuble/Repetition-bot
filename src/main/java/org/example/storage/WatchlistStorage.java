package org.example.storage;

import java.util.List;

public interface WatchlistStorage {

    List<String> getAll(long userId);

    void add(long userId, String title);

    boolean remove(long userId, String title);
}