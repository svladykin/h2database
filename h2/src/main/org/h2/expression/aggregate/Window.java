/*
 * Copyright 2004-2018 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.expression.aggregate;

import java.util.ArrayList;

import org.h2.engine.Session;
import org.h2.expression.Expression;
import org.h2.table.ColumnResolver;
import org.h2.util.StringUtils;
import org.h2.value.Value;
import org.h2.value.ValueArray;

/**
 * Window clause.
 */
public final class Window {

    private final ArrayList<Expression> partitionBy;

    /**
     * Creates a new instance of window clause.
     *
     * @param partitionBy
     *            PARTITION BY clause, or null
     */
    public Window(ArrayList<Expression> partitionBy) {
        this.partitionBy = partitionBy;
    }

    /**
     * Map the columns of the resolver to expression columns.
     *
     * @param resolver
     *            the column resolver
     * @param level
     *            the subquery nesting level
     */
    public void mapColumns(ColumnResolver resolver, int level) {
        if (partitionBy != null) {
            for (Expression e : partitionBy) {
                e.mapColumns(resolver, level);
            }
        }
    }

    /**
     * Returns the key for the current group.
     *
     * @param session
     *            session
     * @return key for the current group, or null
     */
    public ValueArray getCurrentKey(Session session) {
        if (partitionBy == null) {
            return null;
        }
        int len = partitionBy.size();
        Value[] keyValues = new Value[len];
        // update group
        for (int i = 0; i < len; i++) {
            Expression expr = partitionBy.get(i);
            keyValues[i] = expr.getValue(session);
        }
        return ValueArray.get(keyValues);
    }

    /**
     * Returns SQL representation.
     *
     * @return SQL representation.
     */
    public String getSQL() {
        if (partitionBy == null) {
            return "OVER ()";
        }
        StringBuilder builder = new StringBuilder().append("OVER (PARTITION BY ");
        for (int i = 0; i < partitionBy.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(StringUtils.unEnclose(partitionBy.get(i).getSQL()));
        }
        return builder.append(')').toString();
    }

    @Override
    public String toString() {
        return getSQL();
    }

}