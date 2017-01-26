/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */

package org.h2.samples;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.h2.api.ValueType;
import org.h2.api.ErrorCode;
import org.h2.message.DbException;
import org.h2.store.DataHandler;
import org.h2.tools.DeleteDbFiles;
import org.h2.value.Value;
import org.h2.value.ValueJavaObject;
import org.h2.value.ValueNull;
import sun.org.mozilla.javascript.internal.NativeObject;

/**
 * Sample showcasing use of value types.
 *
 * @author apaschenko
 */
public class UserValueTypes {
    public static void main(String[] args) throws Exception {
        // delete the database named 'test' in the user home directory
        DeleteDbFiles.execute("~", "test", true);

        Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection("jdbc:h2:~/test");
        Statement stat = conn.createStatement();

        // Register custom type
        stat.execute("create value type person for " +
            "\"org.h2.samples.UserValueTypes$PersonType\"");

        stat.execute("create table test(id int primary key, p PERSON)");

        // Automatically build object from JSON string
        stat.execute("insert into test(id, p) values (1, '{firstName:\"John\"," +
            "secondName:\"Smith\"}')");

        ResultSet rs;
        rs = stat.executeQuery("select p from test where id = 1");

        rs.next();

        Person p = (Person) rs.getObject(1);

        System.out.println(p.toString());

        stat.execute("drop value type PeRsOn");

        stat.close();
        conn.close();
    }

    private final static class Person implements Serializable {
        public final String firstName;

        public final String secondName;

        private Person(String firstName, String secondName) {
            this.firstName = firstName;
            this.secondName = secondName;
        }

        @Override
        public String toString() {
            return "Person {" +
                "firstName='" + firstName + '\'' +
                ", secondName='" + secondName + '\'' +
                '}';
        }
    }

    public final static class PersonType implements ValueType {
        private DataHandler handler;

        @Override
        public void init(DataHandler dataHandler, ArrayList<String> params) {
            handler = dataHandler;
        }

        @Override
        public Value convert(Value value) {
            if (value == null)
                return ValueNull.INSTANCE;

            switch (value.getType()) {
                case Value.STRING: {
                    return ValueJavaObject.getNoCopy(parsePerson(value.getString()),
                        null, handler);
                }
                default: {
                    throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1);
                }
            }
        }

        private static Person parsePerson(String s) {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            try {
                Object e = engine.eval("person = " + s + ";");

                if (!(e instanceof NativeObject))
                    throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1);

                NativeObject o = (NativeObject) e;

                String firstName = (String) o.get("firstName");

                String secondName = (String) o.get("secondName");

                return new Person(firstName, secondName);
            } catch (DbException e) {
                throw e;
            } catch (Exception e) {
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e);
            }
        }
    }
}
