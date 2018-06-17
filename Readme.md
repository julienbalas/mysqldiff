# MySqlDiff

#### The small application in this repository does :
- read a mysql dump (with or without data).
- print the differences.
- propose SQL statements to make the 2 databases structurally identical.


#### The main avantages over other software are :
* does not need an actual connection to the databases, you can compare production db to development db.
* it's batchable so you can use it in your Continuous Integration build system.
* it's fast : less than a second on a 200+ tables file.
* it works great (at least in my case)


## Usage
1. install jdk, maven
1. git clone this repository
1. mvn package
1. cd target
1. java -jar mysqldiff-1.0-SNAPSHOT.jar dump1.sql dump2.sql \[name1\] \[name2\]

## Known limitations
* doesn't take care of triggers, stored procedure, etc. and i don't think i'm going to do it.
* works well with [phpmyadmin](http://www.phpmyadmin.net/) and [adminer](http://www.adminer.org/) export, not sure it will work with an actual mysql dump (but i'm going to check that at some point)
* has bug(s) on Foreign Key detection
* if a table is missing, the CREATE TABLE statement is not produced (yet)
* if a column/index/foreignkey is renammed it count as 2 differences (for now)
* same for column datatype difference


## RoadMap
* add some command line parameters handling
* add some output option (choose SQL action to make the databases identical by adding or removing stuff)
* ?
