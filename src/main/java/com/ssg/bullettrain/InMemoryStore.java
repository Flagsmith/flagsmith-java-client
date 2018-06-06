package com.ssg.bullettrain;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An implementation of inmemory store for caching feature flags states in memory.
 *
 * Created by Pavlo Maksymchuk.
 */
public class InMemoryStore<T extends Flag> implements CrudStore<T> {

    /**
     * InMemory Feature Flag data
     */
    private final Map<String, Flag> data = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     */
    public InMemoryStore() {
    }


    public synchronized T create(Flag flag) {
        return null;
    }
    public synchronized Flag read(String id) {
        return null;
    }

    public synchronized Flag update(Flag flag) {
        return null;
    }

    public synchronized void delete(String id) {
    }
}
