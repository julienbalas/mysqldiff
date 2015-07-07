package org.jba;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jba.model.Schema;
import org.jba.model.TableKV;

/**
 * Compare 2 mysql dump file and generate the SQL code to make them structuraly identical.
 * 
 */
public class MysqlDiff {

    private static final String REP = "/home/julien/dev/mysqldiff/data/";
    private static final String FIC_A = "2015_06_19_vet1_gmvet.mysql";
    private static final String DEFAULT_NAME_A = "the first database";
    private static final String DEFAULT_NAME_B = "the second database";
    private static final String FIC_B = "2015_06_19_vet2_gmvet.mysql";
    private static final boolean NO_ARG = false;
    private static final boolean WITH_ARG = true;
    // options
    static boolean nc = false; // don't print the comments
    static String nameA = DEFAULT_NAME_A;
    static String nameB = DEFAULT_NAME_B;

    private static Schema buildSchema(String fileName) throws Exception {

        // TODO JBA traiter la présence des fichiers
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
                if (inCreate && line.startsWith(" ")) { // we are on a column or key line
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
        long deb = System.currentTimeMillis();

        Options options = new Options();
        options.addOption("u", NO_ARG, "print usage");
        options.addOption("v", NO_ARG, "print the version");
        options.addOption("nc", NO_ARG, "don't print the comments");
        options.addOption("fa", WITH_ARG, "file name to the A databasedump");
        options.addOption("fb", WITH_ARG, "file name to the B database dump");
        options.addOption("na", WITH_ARG, "name of the A database ");
        options.addOption("nb", WITH_ARG, "name of the B database");
        options.addOption("oa", WITH_ARG, "only print statement for the A database");
        options.addOption("ob", WITH_ARG, "only print statement for the A database");
        
        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);
            nc = line.hasOption("nc");
            if (line.hasOption("u")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("MysqlDiff", options);
                System.exit(0);
            }
            if (line.hasOption("v")) {
                System.out.println("MysqlDiff v 1.0");
                System.exit(0);
            }
            if (line.hasOption("na")) {
                nameA = line.getOptionValue("na");
            }
            if (line.hasOption("nb")) {
                nameB = line.getOptionValue("nb");
            }
        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
        }
        printComment("-- mysqlDiff start");


        Schema schemaA = buildSchema(FIC_A);
        Schema schemaB = buildSchema(FIC_B);
        printComment("-- " + nameA + " = " + FIC_A);
        printComment("-- " + nameB + " = " + FIC_B);

        // the 2 schema are loaded, we can start the real work
        for (String tableName : getAllTables(schemaA, schemaB)) {
            TableKV tableA = schemaA.getTable(tableName);
            TableKV tableB = schemaB.getTable(tableName);

            if (tableA.equals(TableKV.NOT_FOUND)) {
                printComment("-- Table " + tableName + " exists in " + nameB + " but not in " + nameA + ".");
            } else if (tableB.equals(TableKV.NOT_FOUND)) {
                printComment("-- Table " + tableName + " exists in " + nameA + " but not in " + nameB + ".");
            }
        }

        printColDiff(schemaA, nameA, schemaB, nameB);
        printColDiff(schemaB, nameB, schemaA, nameA);

        printIndexDiff(schemaA, nameA, schemaB, nameB);
        printIndexDiff(schemaB, nameB, schemaA, nameA);

        printContrainteDiff(schemaA, nameA, schemaB, nameB);
        printContrainteDiff(schemaB, nameB, schemaA, nameA);

        long fin = System.currentTimeMillis();
        printComment("-- mysqlDiff end : duration = " + displayDuration(deb, fin));
    }

    private static void printComment(String string) {
        if (!nc) {
            System.out.println(string);
        }

    }

    private static void printColDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName) {
        printComment("-- Columns from " + firstName + " who don't exists in " + secondName + ".");
        for (String tableName : getAllTables(firstSchema, secondSchema)) {
            TableKV tableFirst = firstSchema.getTable(tableName);
            TableKV tableSecond = secondSchema.getTable(tableName);

            if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
                // table is on both side, check the columns 
                for (String ligneA : tableFirst.lines) {
                    if (!tableSecond.lines.contains(ligneA)) {
                        if (!ligneA.contains(" KEY `")) {
                            print("ALTER TABLE " + tableFirst.name + " ADD " + ligneA + ";");
                        }
                    }
                }
            }

        }
    }

    private static void printIndexDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName) {
        printComment("-- Indexs from " + firstName + " who don't exists in " + secondName + ".");
        for (String tableName : getAllTables(firstSchema, secondSchema)) {
            TableKV tableFirst = firstSchema.getTable(tableName);
            TableKV tableSecond = secondSchema.getTable(tableName);

            if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
                // table is on both side, check the index
                for (String ligneA : tableFirst.lines) {
                    if (!tableSecond.lines.contains(ligneA)) {
                        if (ligneA.contains(" KEY `")) {
                            print("ALTER TABLE " + tableFirst.name + " ADD " + ligneA.replace("KEY", "INDEX") + ";");
                        }
                    }
                }
            }

        }
    }

    private static void printContrainteDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName) {
        printComment("-- Foreign Key from " + firstName + " who don't exists in " + secondName + ".");
        for (String tableName : getAllConstraints(firstSchema, secondSchema)) {
            TableKV tableFirst = firstSchema.getContrainte(tableName);
            TableKV tableSecond = secondSchema.getContrainte(tableName);
            if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
                // Table is on both side, check the FK
                for (String ligneA : tableFirst.lines) {
                    if (!tableSecond.lines.contains(ligneA)) {
                        print("ALTER TABLE " + tableFirst.name + ligneA + ";");
                    }
                }
            }

        }
    }

    private static void print(String string) {
        System.out.println(string.replaceAll("( )*", " "));

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
