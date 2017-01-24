/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.h2.engine;

import java.util.ArrayList;
import org.h2.api.CustomType;
import org.h2.message.Trace;
import org.h2.table.Table;
import org.h2.util.StatementBuilder;
import org.h2.util.StringUtils;

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
        StatementBuilder buff = new StatementBuilder("CREATE CUSTOM TYPE ")
            .append(getName())
            .append("\n    FOR CLASS '")
            .append(type.getClass().getName())
            .append('\'');

        if (typeParams != null && !typeParams.isEmpty()) {
            buff.append("\n    WITH ");
            buff.resetCount();
            for (String parameter : typeParams) {
                buff.appendExceptFirst(", ");
                buff.append(StringUtils.quoteIdentifier(parameter));
            }
        }

        return buff.toString();
    }

    public CustomType getCustomType() {
        return type;
    }

    @Override
    public String getDropSQL() {
        return "DROP CUSTOM TYPE IF EXISTS " + getName();
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
