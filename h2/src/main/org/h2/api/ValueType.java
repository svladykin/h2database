/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api;

import org.h2.store.DataHandler;
import org.h2.value.Value;
import org.h2.value.ValueUserDefined;

import java.util.ArrayList;

/**
 * Interface for user defined value types to declare their
 * construction logic based on {@link Value}s of built-in types.
 *
 * @author apaschenko
 */
public interface ValueType {
    void init(DataHandler dataHandler, ArrayList<String> params);

    Value convert(Value value);

    Class<? extends ValueUserDefined> getValueClass();
}
