/*
 * Copyright 2004-2017 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.engine;

import java.util.ArrayList;
import org.h2.api.CustomType;
import org.h2.command.Parser;
import org.h2.message.Trace;
import org.h2.table.Table;
import org.h2.util.StatementBuilder;

/**
 * Represents a custom data type
 * (a class with custom construction logic from {@link org.h2.value.Value}s).
 *
 * @author apaschenko
 */
public class CustomDataType extends DbObjectBase {
    private final CustomType type;

    private final ArrayList<String> typeParams;

    public CustomDataType(Database database, int id, String name, CustomType type, ArrayList<String> typeParams) {
        assert type != null;

        this.type = type;
        this.typeParams = typeParams;
        initDbObjectBase(database, id, name, Trace.DATABASE);
    }

    @Override
    public String getCreateSQLForCopy(Table table, String quotedName) {
        return null;
    }

    @Override
    public String getCreateSQL() {
        StatementBuilder buff = new StatementBuilder("CREATE VALUE TYPE ")
            .append(getSQL())
            .append("\n    FOR ")
            .append(Parser.quoteIdentifier(getCustomType().getClass().getName()));

        if (typeParams != null && !typeParams.isEmpty()) {
            buff.append("\n    WITH ");
            buff.resetCount();
            for (String parameter : typeParams) {
                buff.appendExceptFirst(", ");
                buff.append(Parser.quoteIdentifier(parameter));
            }
        }

        return buff.toString();
    }

    public CustomType getCustomType() {
        return type;
    }

    @Override
    public String getDropSQL() {
        return "DROP VALUE TYPE IF EXISTS " + getSQL();
    }

    @Override
    public int getType() {
        return DbObject.CUSTOM_DATATYPE;
    }

    @Override
    public void removeChildrenAndResources(Session session) {
        database.removeMeta(session, getId());
    }

    @Override
    public void checkRename() {
        // ok
    }
}
