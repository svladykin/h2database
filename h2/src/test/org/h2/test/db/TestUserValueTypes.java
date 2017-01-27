/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */

package org.h2.test.db;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.h2.api.ErrorCode;
import org.h2.api.ValueType;
import org.h2.message.DbException;
import org.h2.store.DataHandler;
import org.h2.test.TestBase;
import org.h2.util.StringUtils;
import org.h2.value.Value;
import org.h2.value.ValueJavaObject;
import org.h2.value.ValueNull;

/**
 * Test for user defined value types.
 *
 * @author apaschenko
 */
public class TestUserValueTypes extends TestBase {
    /**
     * Run just this test.
     *
     * @param args ignored
     */
    public static void main(String[] args) throws Exception {
        TestBase.createCaller().init().test();
    }

    private Connection conn;
    private Statement stat;

    @Override
    public void test() throws Exception {
        testInsertAndSelect();
        testCast();
        testNulls();
        testMetadata();
    }

    private void beforeTest() throws SQLException {
        deleteDb("valueTypes");
        conn = getConnection("valueTypes");

        stat = conn.createStatement();

        // Register custom type
        stat.execute("create value type COMPLEX for " +
            "\"org.h2.test.db.TestUserValueTypes$ComplexNumberType\" WITH PARAM1, PARAM2");
    }

    private void afterTest() throws SQLException {
        stat.execute("drop value type CoMpLeX");

        stat.close();
        conn.close();
        deleteDb("valueTypes");
    }

    private void testInsertAndSelect() throws SQLException {
        beforeTest();

        stat.execute("create table test(id int primary key, n COMPLEX)");

        // Automatically build object from JSON string
        stat.execute("insert into test(id, n) values (1, '1.0 + 5.0i'), (2, -500)");

        ResultSet rs;
        rs = stat.executeQuery("select n from test");

        assertTrue(rs.next());
        ComplexNumber p = (ComplexNumber) rs.getObject(1);
        assertEquals(1.0, p.real);
        assertEquals(5.0, p.im);

        assertTrue(rs.next());
        p = (ComplexNumber) rs.getObject(1);
        assertEquals(-500, p.real);
        assertEquals(0, p.im);

        rs.close();

        afterTest();
    }

    private void testCast() throws SQLException {
        beforeTest();

        ResultSet rs;
        rs = stat.executeQuery("select CAST('+.5i' as COMPLEX)");

        assertTrue(rs.next());

        ComplexNumber p = (ComplexNumber) rs.getObject(1);

        assertEquals(0, p.real);
        assertEquals(0.5, p.im);

        rs.close();

        afterTest();
    }

    private void testNulls() throws SQLException {
        beforeTest();

        stat.execute("create table test(id int primary key, n COMPLEX)");

        // Automatically build object from JSON string
        stat.execute("insert into test(id, n) values (1, null)");

        ResultSet rs;
        rs = stat.executeQuery("select n from test where id = 1");

        assertTrue(rs.next());

        ComplexNumber p = (ComplexNumber) rs.getObject(1);

        assertNull(p);

        rs.close();

        afterTest();
    }

    private void testMetadata() throws SQLException {
        beforeTest();

        ResultSet rs;
        rs = stat.executeQuery("select CATALOG, TYPE_NAME, CLASS_NAME, SQL " +
            "from information_schema.value_types;");

        assertTrue(rs.next());

        assertEquals("VALUETYPES", rs.getString(1));
        assertEquals("COMPLEX", rs.getString(2));
        assertEquals("org.h2.test.db.TestUserValueTypes$ComplexNumberType", rs.getString(3));
        assertEquals("CREATE VALUE TYPE COMPLEX FOR \"org.h2.test.db.TestUserValueTypes$" +
            "ComplexNumberType\" WITH PARAM1, PARAM2", rs.getString(4).replaceAll("\\s+", " "));

        rs.close();

        afterTest();
    }

    public final static class ComplexNumber implements Serializable {
        public final double real;

        public final double im;

        public ComplexNumber(double real, double im) {
            this.real = real;
            this.im = im;
        }
    }

    public final static class ComplexNumberType implements ValueType {
        private DataHandler handler;

        @Override
        public void init(DataHandler dataHandler, ArrayList<String> params) {
            handler = dataHandler;
        }

        @Override
        public Value convert(Value value) {
            if (value == null || value == ValueNull.INSTANCE)
                return ValueNull.INSTANCE;

            switch (value.getType()) {
                case Value.INT:
                case Value.DOUBLE:
                case Value.FLOAT:
                case Value.DECIMAL:
                case Value.LONG:
                case Value.SHORT: {
                    return ValueJavaObject.getNoCopy(new ComplexNumber(value.getDouble(), 0),
                        null, handler);
                }
                case Value.STRING: {
                    if (StringUtils.isNullOrEmpty(value.getString()))
                        return ValueNull.INSTANCE;

                    return ValueJavaObject.getNoCopy(parseComplexNumber(value.getString()),
                        null, handler);
                }
                default: {
                    throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1);
                }
            }
        }

        /**
         * Simple parser for complex numbers. Both real and im components
         * must be written in non scientific notation.
         * @param s String.
         * @return {@link ComplexNumber} object.
         */
        private static ComplexNumber parseComplexNumber(String s) {
            if (StringUtils.isNullOrEmpty(s))
                return null;

            s = s.replaceAll("\\s","");

            boolean hasIm = (s.charAt(s.length() - 1) == 'i');

            int signs = 0;

            int pos = 0;

            int maxSignPos = -1;

            while (pos != -1) {
                pos = s.indexOf('-', pos);
                if (pos != -1) {
                    signs++;
                    maxSignPos = Math.max(maxSignPos, pos++);
                }
            }

            pos = 0;

            while (pos != -1) {
                pos = s.indexOf('+', pos);
                if (pos != -1) {
                    signs++;
                    maxSignPos = Math.max(maxSignPos, pos++);
                }
            }

            if (signs > 2 || (signs == 2 && !hasIm))
                throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1);

            double real;
            double im;

            if (signs == 0 || (signs == 1 && maxSignPos == 0)) {
                if (hasIm) {
                    real = 0;
                    im = Double.parseDouble(s.substring(0, s.length() - 1));
                } else {
                    real = Double.parseDouble(s);
                    im = 0;
                }
            } else {
                real = Double.parseDouble(s.substring(0, maxSignPos));
                im = Double.parseDouble(s.substring(maxSignPos, s.length() - 1));
            }

            return new ComplexNumber(real, im);
        }
    }
}
