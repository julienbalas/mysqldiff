package org.jba.model;

import java.util.HashSet;
import java.util.Set;

public class Schema {
    public String fileName;
    public Set<TableKV> tables = new HashSet<TableKV>();
    public Set<TableKV> constraints = new HashSet<TableKV>();

    public TableKV getTable(String name) {
        for (TableKV cur : tables) {
            if (cur.name.equals(name)) {
                return cur;
            }
        }
        return TableKV.NOT_FOUND;
    }

    public TableKV getContrainte(String name) {
        for (TableKV cur : constraints) {
            if (cur.name.equals(name)) {
                return cur;
            }
        }
        return TableKV.NOT_FOUND;
    }
}
