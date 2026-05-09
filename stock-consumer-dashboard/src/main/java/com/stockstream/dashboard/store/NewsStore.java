package com.stockstream.dashboard.store;

import com.stockstream.core.model.NewsEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class NewsStore {

    private static final int MAX_NEWS = 100;
    private final Deque<NewsEvent> store = new ArrayDeque<>(MAX_NEWS + 1);

    public synchronized void add(NewsEvent event) {
        store.addLast(event);
        if (store.size() > MAX_NEWS) store.removeFirst();
    }

    public synchronized List<NewsEvent> getRecent(int limit) {
        int skip = Math.max(0, store.size() - limit);
        return store.stream().skip(skip).toList();
    }
}
