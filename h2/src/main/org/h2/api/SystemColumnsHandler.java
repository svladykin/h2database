/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api;

import org.h2.table.Column;
import org.h2.table.Table;

/**
 * Custom mechanism for resolving system columns
 *
 */
public interface SystemColumnsHandler {
    /**
     * Gets system columns for the given table
     *
     * @param table the table to get system columns for
     * @return the array of system columns for the specified table
     */
    Column[] getSystemColumns(Table table);

    /**
     * Gets system column for the given table and column name
     * @param table the table to get system column for
     * @param name the name of column to get
     * @return the system column
     */
    Column getSystemColumn(Table table, String name);
}
