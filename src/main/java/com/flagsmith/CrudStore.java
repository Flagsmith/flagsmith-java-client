package com.flagsmith;

/**
 * Interface for a basic store to support persistence for Flags
 *
 * Created by Pavlo Maksymchuk.
 */
public interface CrudStore<T extends Flag> {

    T create(Flag flag);

    Flag read(String id);

    Flag update(Flag flag);

    void delete(String id);
}
