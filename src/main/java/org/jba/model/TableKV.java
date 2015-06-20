package org.jba.model;

import java.util.HashSet;
import java.util.Set;

public class TableKV {

    public static final TableKV NOT_FOUND = new TableKV("NOT_FOUND");

    public String name;
    public Set<String> lines = new HashSet<String>();

    public TableKV(String name) {
        this.name = name;
    }
}
