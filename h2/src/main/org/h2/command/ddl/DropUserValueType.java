/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import org.h2.api.ErrorCode;
import org.h2.command.CommandInterface;
import org.h2.engine.UserValueType;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.message.DbException;

/**
 * This class represents the statement DROP VALUE TYPE
 *
 * @author apaschenko
 */
public class DropUserValueType extends DefineCommand {
    private String typeName;
    private boolean ifExists;

    public DropUserValueType(Session session) {
        super(session);
    }

    public void setIfExists(boolean ifExists) {
        this.ifExists = ifExists;
    }

    @Override
    public int update() {
        session.getUser().checkAdmin();
        session.commit(true);
        Database db = session.getDatabase();
        UserValueType type = db.findUserValueType(typeName);
        if (type == null) {
            if (!ifExists) {
                throw DbException.get(ErrorCode.USER_VALUE_TYPE_NOT_FOUND_1, typeName);
            }
        } else {
            db.removeDatabaseObject(session, type);
        }
        return 0;
    }

    public void setTypeName(String name) {
        this.typeName = name;
    }

    @Override
    public int getType() {
        return CommandInterface.DROP_VALUE_TYPE;
    }

}
