/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api;

import org.h2.store.DataHandler;
import org.h2.value.Value;

import java.util.ArrayList;

/**
 * Interface for custom types to define their construction logic
 * based on more 'primitive' {@link Value}s.
 *
 * @author apaschenko
 */
public interface CustomType {
    void init(DataHandler dataHandler, ArrayList<String> params);

    Value convert(Value value);
}
