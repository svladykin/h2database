/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import java.util.ArrayList;
import org.h2.api.CustomType;
import org.h2.api.ErrorCode;
import org.h2.command.CommandInterface;
import org.h2.engine.CustomDataType;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.util.JdbcUtils;

/**
 * This class represents the statement
 * CREATE CUSTOM TYPE
 *
 * @author apaschenko
 */
public class CreateCustomDataType extends DefineCommand {

    private String typeName;
    private String typeClassName;
    private ArrayList<String> typeParams;
    private boolean ifNotExists;

    public CreateCustomDataType(Session session) {
        super(session);
    }

    public void setTypeName(String name) {
        this.typeName = name;
    }

    public void setTypeClassName(String typeClassName) {
        this.typeClassName = typeClassName;
    }

    public void setTypeParams(ArrayList<String> typeParams) {
        this.typeParams = typeParams;
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
        if (db.findCustomDataType(typeName) != null) {
            if (ifNotExists) {
                return 0;
            }
            throw DbException.get(
                    ErrorCode.USER_DATA_TYPE_ALREADY_EXISTS_1,
                    typeName);
        }

        CustomType type;

        try {
            type = (CustomType) JdbcUtils.loadUserClass(typeClassName).newInstance();
            type.init(session.getDataHandler(), typeParams);
        } catch (Exception e) {
            throw DbException.convert(e);
        }

        int id = getObjectId();

        CustomDataType customDataType = new CustomDataType(db, id, typeName, type, typeParams);

        db.addDatabaseObject(session, customDataType);

        return 0;
    }

    @Override
    public int getType() {
        return CommandInterface.CREATE_CUSTOM_TYPE;
    }

}
