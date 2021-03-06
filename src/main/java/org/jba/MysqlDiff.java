package org.jba;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.jba.model.Schema;
import org.jba.model.TableKV;

/**
 * Compare 2 mysql dump file and generate the SQL code to make them structurally identical.
 * 
 */
public class MysqlDiff {

	private static final String DEFAULT_FILE_A = "a.sql";
	private static final String DEFAULT_FILE_B = "b.sql";
	private static final String DEFAULT_NAME_A = "the first database";
	private static final String DEFAULT_NAME_B = "the second database";
	private static final boolean NO_ARG = false;
	private static final boolean WITH_ARG = true;

	static boolean noComment = false; // don't print the comments
	static boolean printA = true;
	static boolean printB = true;
	static String nameA = DEFAULT_NAME_A;
	static String nameB = DEFAULT_NAME_B;
	static String fileNameA = DEFAULT_FILE_A;
	static String fileNameB = DEFAULT_FILE_B;

	private static Schema buildSchema(String fileName) throws Exception {
		try {
			FileUtils.openInputStream(new File(fileName));
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println("Error opening file : '" + fileName + "'");
			System.exit(1);
		}
		Schema sch = new Schema();
		sch.fileName = fileName;
		BufferedReader input = null;
		try {
			input = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));

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
		} finally {
			if (input != null) {
				input.close();
			}
		}
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
		options.addOption("fA", WITH_ARG, "file name to the A databasedump");
		options.addOption("fB", WITH_ARG, "file name to the B database dump");
		options.addOption("nA", WITH_ARG, "name of the A database ");
		options.addOption("nB", WITH_ARG, "name of the B database");
		options.addOption("onlyA", NO_ARG, "only print statement for the A database");
		options.addOption("onlyB", NO_ARG, "only print statement for the B database");

		CommandLineParser parser = new DefaultParser();
		try {
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);
			if (line.hasOption("u")) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("MysqlDiff", options);
				System.exit(0);
			}
			if (line.hasOption("v")) {
				System.out.println("MysqlDiff v 1.0");
				System.exit(0);
			}
			noComment = line.hasOption("nc");
			nameA = line.getOptionValue("nA", DEFAULT_NAME_A);
			nameB = line.getOptionValue("nB", DEFAULT_NAME_B);
			fileNameA = line.getOptionValue("fA", DEFAULT_FILE_A);
			fileNameB = line.getOptionValue("fB", DEFAULT_FILE_B);
			printB = !line.hasOption("onlyA");
			printA = !line.hasOption("onlyB");

		} catch (ParseException exp) {
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			System.exit(1);
		}
		printComment("-- mysqlDiff start " + new Date().toString());

		Schema schemaA = buildSchema(fileNameA);
		Schema schemaB = buildSchema(fileNameB);
		printComment("-- " + nameA + " = " + fileNameA);
		printComment("-- " + nameB + " = " + fileNameB);

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

		printColDiff(schemaA, nameA, schemaB, nameB, printB);
		printColDiff(schemaB, nameB, schemaA, nameA, printA);

		printIndexDiff(schemaA, nameA, schemaB, nameB, printB);
		printIndexDiff(schemaB, nameB, schemaA, nameA, printA);

		printContrainteDiff(schemaA, nameA, schemaB, nameB, printB);
		printContrainteDiff(schemaB, nameB, schemaA, nameA, printA);

		long fin = System.currentTimeMillis();
		printComment("-- mysqlDiff end : duration = " + displayDuration(deb, fin));
	}

	private static void printComment(String string) {
		if (!noComment) {
			System.out.println(string);
		}

	}

	private static void printColDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName, boolean print) {
		if (!print) {
			return;
		}
		printComment("-- Columns from " + firstName + " who don't exists in " + secondName + ".");
		for (String tableName : getAllTables(firstSchema, secondSchema)) {
			TableKV tableFirst = firstSchema.getTable(tableName);
			TableKV tableSecond = secondSchema.getTable(tableName);

			if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
				// table is on both side, check the columns
				for (String ligneA : tableFirst.lines) {
					if (!tableSecond.lines.contains(ligneA) && !ligneA.contains(" KEY `")) {
						print("ALTER TABLE " + tableFirst.name + " ADD " + ligneA + ";");

					}
				}
			}
		}
	}

	private static void printIndexDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName, boolean print) {
		if (!print) {
			return;
		}
		printComment("-- Indexs from " + firstName + " who don't exists in " + secondName + ".");
		for (String tableName : getAllTables(firstSchema, secondSchema)) {
			TableKV tableFirst = firstSchema.getTable(tableName);
			TableKV tableSecond = secondSchema.getTable(tableName);

			if (!tableFirst.equals(TableKV.NOT_FOUND) && !tableSecond.equals(TableKV.NOT_FOUND)) {
				// table is on both side, check the index
				for (String ligneA : tableFirst.lines) {
					if (!tableSecond.lines.contains(ligneA) && ligneA.contains(" KEY `")) {
						print("ALTER TABLE " + tableFirst.name + " ADD " + ligneA.replace("KEY", "INDEX") + ";");

					}
				}
			}

		}
	}

	private static void printContrainteDiff(Schema firstSchema, String firstName, Schema secondSchema, String secondName, boolean print) {
		if (!print) {
			return;
		}
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
		System.out.println(string.replace("   ", " ").replace("  ", " "));

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
		return !line.startsWith("INSERT");
	}
}
