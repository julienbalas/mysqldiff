package org.jba;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import org.jba.model.Schema;
import org.jba.model.TableKV;

/**
 * Compare 2 mysql dump file and generate the SQL code to make them structuraly identical.
 * 
 */
public class DiffSchema {

    private static final String REP = "/home/julien/dev/mysqldiff/data/";
    private static final String FIC_A = "firstFile.sql";
    private static final String NAME_A = "my first platform";
    private static final String NAME_B = "my second platform";
    private static final String FIC_B = "Secondfile.sql";

    private static Schema buildSchema(String fileName) throws Exception {
		BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(REP + fileName)));
        Schema sch = new Schema();
        sch.fileName = fileName;

        boolean inCreate = false;
        boolean inAlter = false;
        String line = null;
        TableKV table = null;
        TableKV constraint = null;
        while ((line = input.readLine()) != null) {
            if (isStructure(line)) {
            	// Tables
                if (line.startsWith("CREATE TABLE ")) { // Table creation block start
                    inCreate = true;
                    table = new TableKV(extractName(line));
                }
                if (inCreate && line.startsWith(" ")) { // we are on a colomn or key line
                    table.lines.add(clean(line));
                }
                if (inCreate && line.startsWith(") ENGINE=")) { // Table creation block end
                    inCreate = false;
                    sch.tables.add(table);

                }
                // Foreign keys
                if (line.startsWith("ALTER TABLE ")) { // Alter block start
                    inAlter = true;
                    constraint = new TableKV(extractName(line));
                }
                if (inAlter && line.startsWith(" ")) {
                    constraint.lines.add(clean(line));
                }
                if (inAlter && line.contains(";")) { // Alter block end
                    inAlter = false;
                    sch.constraints.add(constraint);
                }
            }
        }
        input.close();
        return sch;
    }

    private static String clean(String line) {
        return line.replace(",", "").replace(";", "");
    }

    private static String extractName(String line) {
        String[] tab = line.split("`");
        return tab[1];
    }

    public static void main(String[] args) throws Exception {
		System.out.println("-- mysqlDiff start");
        long deb = System.currentTimeMillis();

        Schema schemaA = buildSchema(FIC_A);
        Schema schemaB = buildSchema(FIC_B);
        System.out.println(NAME_A + " = " + FIC_A);
        System.out.println(NAME_B + " = " + FIC_B);

        // maintenant qu'on a les deux schema en RAM, on vas les comparer
        // on le fait en X passes, même si ca n'est pas forcement le plus efficace, comme ca on peut grouper le type de modifs
        // d'abord l'absence ou la presence des tables elles même
        for (String tableName : getAllTables(schemaA, schemaB)) {
            TableKV tableA = schemaA.getTable(tableName);
            TableKV tableB = schemaB.getTable(tableName);

            if (tableA.equals(TableKV.NOT_FOUND)) {
                System.out.println("-- La table " + tableName + " existe dans " + NAME_B + " mais pas dans " + NAME_A + ".");
            } else if (tableB.equals(TableKV.NOT_FOUND)) {
                System.out.println("-- La table " + tableName + " existe dans " + NAME_A + " mais pas dans " + NAME_B + ".");
            }
        }
        
        printColDiff(schemaA, NAME_A, schemaB, NAME_B);
        printColDiff(schemaB, NAME_B, schemaA, NAME_A);

        printIndexDiff(schemaA, NAME_A, schemaB, NAME_B);
        printIndexDiff(schemaB, NAME_B, schemaA, NAME_A);

        printContrainteDiff(schemaA, NAME_A, schemaB, NAME_B);
        printContrainteDiff(schemaB, NAME_B, schemaA, NAME_A);
       

        long fin = System.currentTimeMillis();
		System.out.println("-- mysqlDiff end : duration = " + displayDuration(deb, fin));
    }

    private static void printColDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName) {
        System.out.println("-- Les colonnes de " + firstName + " qui n'existent pas dans " + secondName + ".");
        for (String tableName : getAllTables(firstSchema, secondSchema)) {
            TableKV tableFirst = firstSchema.getTable(tableName);
            TableKV tableSecond = secondSchema.getTable(tableName);

            if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
                // elle est des deux cotés
                // on regarde si on a les même colonnes
                for (String ligneA : tableFirst.lines) {
                    if (!tableSecond.lines.contains(ligneA)) {
                        if (!ligneA.contains(" KEY `")) {
                            System.out.println("ALTER TABLE " + tableFirst.name + " ADD " + ligneA + ";");
                        }
                    }
                }
            }
            
        }
    }

    private static void printIndexDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName) {
        System.out.println("-- Les index de " + firstName + " qui n'existent pas dans " + secondName + ".");
        for (String tableName : getAllTables(firstSchema, secondSchema)) {
            TableKV tableFirst = firstSchema.getTable(tableName);
            TableKV tableSecond = secondSchema.getTable(tableName);

            if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
                // elle est des deux cotés
                // on regarde si on a les même colonnes
                for (String ligneA : tableFirst.lines) {
                    if (!tableSecond.lines.contains(ligneA)) {
                        if (ligneA.contains(" KEY `")) {
                            System.out.println("ALTER TABLE " + tableFirst.name + " ADD " + ligneA.replace("KEY", "INDEX") + ";");
                        }
                    }
                }
            }

        }
    }

    private static void printContrainteDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName) {
        System.out.println("-- Les Foreign Key de " + firstName + " qui n'existent pas dans " + secondName + ".");
        for (String tableName : getAllConstraints(firstSchema, secondSchema)) {
            TableKV tableFirst = firstSchema.getContrainte(tableName);
            TableKV tableSecond = secondSchema.getContrainte(tableName);
            if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
                // elle est des deux cotés
                // on regarde si on a les même colonnes
                for (String ligneA : tableFirst.lines) {

                    if (!tableSecond.lines.contains(ligneA)) {
                        System.out.println("ALTER TABLE " + tableFirst.name + ligneA + ";");
                    }
                }
            }

        }
    }

    private static Set<String> getAllTables(Schema firstSchema, Schema secondSchema) {
        Set<String> tables = new TreeSet<String>();
        for (TableKV curTable : firstSchema.tables) {
            tables.add(curTable.name);
        }
        for (TableKV curTable : secondSchema.tables) {
            tables.add(curTable.name);
        }
        return tables;
    }


    private static Set<String> getAllConstraints(Schema firstSchema, Schema secondSchema) {
        Set<String> tables = new TreeSet<String>();
        for (TableKV curTable : firstSchema.constraints) {
            tables.add(curTable.name);
        }
        for (TableKV curTable : secondSchema.constraints) {
            tables.add(curTable.name);
        }
        return tables;
    }


    public static String displayDuration(long debut, long fin) {
        return (fin - debut) + " ms, or " + (fin - debut) / 1000 + " seconds, or " + (fin - debut) / (1000 * 60) + " minutes.";
    }

    private static boolean isStructure(String line) {
        if (line.startsWith("INSERT")) {
            return false;
        }
        return true;
    }
}
