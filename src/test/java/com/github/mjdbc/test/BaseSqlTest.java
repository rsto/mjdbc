package com.github.mjdbc.test;

import com.github.mjdbc.DbImpl;
import com.github.mjdbc.test.util.DbUtils;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public abstract class BaseSqlTest<S> extends Assert {
    /**
     * Low level connection pool.
     */
    protected HikariDataSource ds;


    /**
     * Set of raw SQL queries.
     */
    protected S sql;

    @NotNull
    protected final Class<S> type;
    private String schemaFileName;

    public BaseSqlTest(@NotNull Class<S> type, String schemaFileName) {
        this.type = type;
        this.schemaFileName = schemaFileName;
    }

    @Before
    public void setUp() {
        ds = DbUtils.prepareDataSource(schemaFileName);
        DbImpl db = new DbImpl(ds);
        sql = db.attachSql(type);
    }

    @After
    public void tearDown() {
        ds.close();
    }
}
