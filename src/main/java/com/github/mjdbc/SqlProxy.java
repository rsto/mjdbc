package com.github.mjdbc;

import com.github.mjdbc.DbImpl.SqlOp;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Proxy for {@link Sql} method. Manages execution of SQL query.
 */
class SqlProxy implements InvocationHandler {
    @NotNull
    private final DbImpl db;

    @NotNull
    private final String interfaceSimpleName;

    SqlProxy(@NotNull DbImpl db, @NotNull String interfaceSimpleName) {
        this.db = db;
        this.interfaceSimpleName = interfaceSimpleName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        SqlOp p = db.opByMethod.get(method);
        if (p == null) { // java.lang.Object methods
            if (method.getName().equals("toString")) {
                return interfaceSimpleName + "$Proxy";
            }
            return method.invoke(this, args);
        }
        long t0 = System.nanoTime();
        try {
            return db.execute(c -> {
                //noinspection unchecked
                DbPreparedStatement s = new DbPreparedStatement(c, p.parsedSql, p.resultMapper, p.parametersNamesMapping, p.returnGeneratedKeys);
                if (args != null) {
                    if (p.batchIteratorFactory != null) {
                        return executeBatch(p, s, args);
                    }
                    bindSingleStatementArgs(p, s, args);
                }
                if (p.resultMapper != Mappers.VoidMapper) {
                    if (p.returnGeneratedKeys) {
                        return s.updateAndGetGeneratedKeys();
                    } else if (p.useUpdateCall) {
                        return s.update();
                    } else {
                        Object res = s.query();
                        return res == null && p.resultMapper.getClass() == ListMapper.class ? new ArrayList<>() : res;
                    }
                }
                s.update();
                return null;
            });
        } finally {
            db.updateTimer(method, t0);
        }
    }

    private static Object executeBatch(@NotNull SqlOp p, @NotNull DbPreparedStatement s, @NotNull Object[] args) throws IllegalAccessException, InvocationTargetException, java.sql.SQLException {
        assert p.batchIteratorFactory != null;
        Object batchArg = args[p.batchArgIdx];
        //noinspection unchecked
        Object[] batchArgs = Arrays.copyOf(args, args.length);
        Iterator it = p.batchIteratorFactory.createIterator(batchArg);
        for (int i = 0; it.hasNext(); i++) {
            Object beanValue = it.next();
            batchArgs[p.batchArgIdx] = beanValue;
            bindSingleStatementArgs(p, s, batchArgs);
            s.statement.addBatch();
            if (i % p.batchSize == p.batchSize - 1 || !it.hasNext()) {
                s.statement.executeBatch();
            }
        }
        return null; //todo: return valid batch results
    }

    private static void bindSingleStatementArgs(@NotNull SqlOp p, @NotNull DbPreparedStatement s, @NotNull Object[] args) throws IllegalAccessException, InvocationTargetException, java.sql.SQLException {
        for (DbImpl.BindInfo bi : p.bindings) {
            List<Integer> sqlIndexes = p.parametersNamesMapping.get(bi.mappedName);
            if (sqlIndexes == null) {
                continue;
            }
            DbPreparedStatement.bindArg(s, bi, sqlIndexes, args[bi.argumentIdx]);
        }
    }
}
