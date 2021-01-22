package com.flagsmith;

/**
 * Created by Pavlo Maksymchuk.
 */
public class Flagsmith {

    /**
     * Storage to persist feature within {@link CrudStore}.
     */
    private CrudStore store = new InMemoryStore();
}
