# MySqlDiff

Over the years i've needed several time a very basic database structure comparison.

Such applications already exists, but :
* Some run only on windows and don't work using wine.
* Some are open source but don't work on my computer.
* Some need an actual connection to the databases and sometimes the databases i want to compare are offsite or behind a firewall.

In the past, i managed to mysqldump the databases, grep the structure only and diff the output. But it was mostly to check if the databases where in sync. In some other cases where the connection to the databases where possible i wrote a programm to scan the database internal data to check for differences. This one was pretty cool but i forgot to ask my client the premission to opensource it. And it was only for sybase.

#### The small application in this repository does :
* read a mysql dump (with or without data).
* print the differences.
* propose SQL statements to make the 2 databases structuraly identical.

If you look at the code, it's very crud and at the same time quite elegant (i think).

#### The main avantages i see are :
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
* works well with phpmyadmin export, not sure it will work with an actual mysql dump (but i'm going to check that at some point)


## RoadMap
* add some command line parameters handling
* add some output option
* ?

