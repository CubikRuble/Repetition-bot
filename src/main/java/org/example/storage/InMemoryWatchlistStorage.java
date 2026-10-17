package org.example.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryWatchlistStorage implements WatchlistStorage {

    private final Map<Long, List<String>> watchlists = new HashMap<>();

    @Override
    public synchronized List<String> getAll(long userId) {
        return List.copyOf(watchlists.getOrDefault(userId, List.of()));
    }

    @Override
    public synchronized void add(long userId, String title) {
        watchlists.computeIfAbsent(userId, id -> new ArrayList<>()).add(title);
    }

    @Override
    public synchronized boolean remove(long userId, String title) {
        List<String> titles = watchlists.get(userId);

        return titles != null && titles.remove(title);
    }
}