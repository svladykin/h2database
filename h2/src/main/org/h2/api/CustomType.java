package org.h2.api;

import org.h2.value.Value;

import java.util.ArrayList;

/**
 * Interface for custom types to define their "parsing" and comparison logic.
 *
 * @author apaschenko
 */
public interface CustomType {
    void setup(ArrayList<String> params);

    Value wrap(Value value);

    Value compare(Value left, Value right);
}
