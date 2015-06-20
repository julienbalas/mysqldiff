package org.jba;



import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import org.jba.model.Schema;
import org.jba.model.TableKV;

/**
 * Comparaison de 2 dump de schema mysql.
 */
public class DiffSchema {

    private static final String REP = "/home/julien/dev/mysqldiff2/data/";
    private static final String FIC_A = "2015_06_19_vet1_gmvet.sql";
    private static final String NOM_A = "vet1";
    private static final String NOM_B = "vet2";
    private static final String FIC_B = "2015_06_19_vet2_gmvet.sql";

    private static Schema buildSchema(String fileName) throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(REP + fileName)));
        Schema sch = new Schema();
        sch.fileName = fileName;

        boolean inCreate = false;
        boolean inAlter = false;
        String line = null;
        TableKV table = null;
        TableKV contrainte = null;
        while ((line = lit(input)) != null) {
            if (isStructure(line)) {
                if (line.startsWith("CREATE TABLE ")) { // debut bloc create
                    inCreate = true;
                    //System.out.println(extractName(line));
                    table = new TableKV(extractName(line));
                }
                if (inCreate && line.startsWith(" ")) { // sur une colonne ou une clé
                    table.lines.add(clean(line));
                }
                if (inCreate && line.startsWith(") ENGINE=")) { // fin bloc create
                    inCreate = false;
                    sch.tables.add(table);

                }
                // les alters
                if (line.startsWith("ALTER TABLE ")) { // debut bloc ALTER
                    inAlter = true;
                    contrainte = new TableKV(extractName(line));
                }
                if (inAlter && line.startsWith(" ")) {
                    contrainte.lines.add(clean(line));
                    System.out.println(clean(line));
                }
                if (inAlter && line.contains(";")) { // fin bloc alter
                    inAlter = false;
                    sch.contraintes.add(contrainte);
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
        System.out.println("# debut");
        long deb = System.currentTimeMillis();

        Schema schemaA = buildSchema(FIC_A);
        Schema schemaB = buildSchema(FIC_B);
        System.out.println(NOM_A + " = " + FIC_A);
        System.out.println(NOM_B + " = " + FIC_B);

        // maintenant qu'on a les deux schema en RAM, on vas les comparer
        // on le fait en X passes, même si ca n'est pas forcement le plus efficace, comme ca on peut grouper le type de modifs
        // d'abord l'absence ou la presence des tables elles même
        for (String tableName : getAllTables(schemaA, schemaB)) {
            TableKV tableA = schemaA.getTable(tableName);
            TableKV tableB = schemaB.getTable(tableName);

            if (tableA.equals(TableKV.NOT_FOUND)) {
                System.out.println("-- La table " + tableName + " existe dans " + NOM_B + " mais pas dans " + NOM_A + ".");
            } else if (tableB.equals(TableKV.NOT_FOUND)) {
                System.out.println("-- La table " + tableName + " existe dans " + NOM_A + " mais pas dans " + NOM_B + ".");
            }
        }
        
        printColDiff(schemaA, NOM_A, schemaB, NOM_B);
        printColDiff(schemaB, NOM_B, schemaA, NOM_A);

        printIndexDiff(schemaA, NOM_A, schemaB, NOM_B);
        printIndexDiff(schemaB, NOM_B, schemaA, NOM_A);

        printContrainteDiff(schemaA, NOM_A, schemaB, NOM_B);
        printContrainteDiff(schemaB, NOM_B, schemaA, NOM_A);
       
        System.out.println("# fin");
        long fin = System.currentTimeMillis();
        System.out.println("# durée = " + displayDuration(deb, fin));
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
        for (String tableName : getAllContraintes(firstSchema, secondSchema)) {
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


    private static Set<String> getAllContraintes(Schema firstSchema, Schema secondSchema) {
        Set<String> tables = new TreeSet<String>();
        for (TableKV curTable : firstSchema.contraintes) {
            tables.add(curTable.name);
        }
        for (TableKV curTable : secondSchema.contraintes) {
            tables.add(curTable.name);
        }
        return tables;
    }

    private static String lit(BufferedReader input) throws IOException {
        return input.readLine();
    }

    public static String displayDuration(long debut, long fin) {
        return (fin - debut) + " ms ou " + (fin - debut) / 1000 + " secondes soit " + (fin - debut) / (1000 * 60) + " minutes.";
    }

    private static boolean isStructure(String line) {
        if (line.startsWith("INSERT")) {
            return false;
        }
        return true;
    }
}
