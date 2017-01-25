package org.h2.api;

import org.h2.store.DataHandler;
import org.h2.value.Value;

import java.util.ArrayList;

/**
 * Interface for custom types to define their "parsing" and comparison logic.
 *
 * @author apaschenko
 */
public interface CustomType {
    void init(DataHandler dataHandler, ArrayList<String> params);

    Value convert(Value value);
}
