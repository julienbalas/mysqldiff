# MySqlDiff

#### The small application in this repository does :
* read a mysql dump (with or without data).
* print the differences.
* propose SQL statements to make the 2 databases structuraly identical.

If you look at the code, it's very crud and at the same time quite elegant (i think).

#### The main avantages over other software are :
* does not need an actual connection to the databases, you can compare production db to developpment db.
* it's batchable so you can use it in your Continuous Integration build system.
* it's fast : less than a second on a 200+ tables file.
* it works great (at least in my case)


## Usage 
1. mvn package
2. cd ==**sdhjlkfhljfsdqkdhfl**==
2. java -jar mysqlDiff dump1.sql dump2.sql [name1] [name2]

## Known limitations
* doesn't take care of triggers, stored procedure, etc. and i don't think i'm going to do it.
* works well with [phpmyadmin](http://www.phpmyadmin.net/) and [adminer](http://www.adminer.org/) export, not sure it will work with an actual mysql dump (but i'm going to check that at some point)
* has bug(s) on Foreign Key detection


## RoadMap
* add some command line parameters handling
* add some output option (choose SQL action to make the databases identical by adding or removing stuff) 
* ?

