/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import java.util.ArrayList;
import org.h2.api.ErrorCode;
import org.h2.command.CommandInterface;
import org.h2.engine.UserValueType;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.table.Table;
import org.h2.util.JdbcUtils;
import org.h2.value.DataType;

/**
 * This class represents the statement CREATE VALUE TYPE
 *
 * @author apaschenko
 */
public class CreateUserValueType extends DefineCommand {

    private String name;
    private String className;
    private ArrayList<String> params;
    private boolean ifNotExists;

    public CreateUserValueType(Session session) {
        super(session);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setParams(ArrayList<String> params) {
        this.params = params;
    }

    public void setIfNotExists(boolean ifNotExists) {
        this.ifNotExists = ifNotExists;
    }

    @Override
    public int update() {
        session.getUser().checkAdmin();
        session.commit(true);
        Database db = session.getDatabase();
        session.getUser().checkAdmin();
        if (db.findUserValueType(name) != null) {
            if (ifNotExists) {
                return 0;
            }
            throw DbException.get(
                    ErrorCode.USER_VALUE_TYPE_ALREADY_EXISTS_1,
                name);
        }

        // Check built-in types
        DataType builtIn = DataType.getTypeByName(name);
        if (builtIn != null) {
            if (!builtIn.hidden) {
                throw DbException.get(
                        ErrorCode.USER_VALUE_TYPE_ALREADY_EXISTS_1,
                    name);
            }
            Table table = session.getDatabase().getFirstUserTable();
            if (table != null) {
                throw DbException.get(
                        ErrorCode.USER_VALUE_TYPE_ALREADY_EXISTS_1,
                        name + " (" + table.getSQL() + ")");
            }
        }

        org.h2.api.ValueType type;

        try {
            type = (org.h2.api.ValueType) JdbcUtils.loadUserClass(className).newInstance();
            type.init(session.getDataHandler(), params);
        } catch (Exception e) {
            throw DbException.convert(e);
        }

        int id = getObjectId();

        UserValueType userValueType = new UserValueType(db, id, name, type, params);

        db.addDatabaseObject(session, userValueType);

        return 0;
    }

    @Override
    public int getType() {
        return CommandInterface.CREATE_VALUE_TYPE;
    }
}
