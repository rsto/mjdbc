package com.github.mjdbc;

import org.jetbrains.annotations.NotNull;

/**
 * Database operation with no result.
 */
public interface DbOpV {
    void run(@NotNull DbConnection c) throws Exception;
}
