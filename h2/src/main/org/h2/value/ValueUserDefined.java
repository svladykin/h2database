/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value;

import org.h2.api.ValueType;

/**
 * @author apaschenko
 */
public abstract class ValueUserDefined extends Value {

    private final int typeId;

    private transient ValueType type;

    protected ValueUserDefined(int typeId) {
        this.typeId = typeId;
    }

    @Override
    public int getType() {
        return typeId;
    }

    public ValueType getValueType() {
        return type;
    }

    @Override
    public final boolean isUserDefinedType() {
        return true;
    }
}
