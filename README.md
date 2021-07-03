<meta name="robots" content="noindex">

# Eris
Measuring discord among multidimensional sources

# Setup

* Scala - v2.13 (https://www.scala-lang.org/download) 

With JVM 11 should work. If Scala is correctly installed, typing `scala` and then something like `1+1` at the resulting prompt should result in something like
`val res0 : Int = 2` being printed.

* Python - v3.6 (https://www.python.org) and libraries `numpy`, `scipy` and `oscp`

  Tested with Python 3.6; doing `pip install numpy scipy oscp` should suffice.
  
* SBT - v1.3.4 (https://www.scala-sbt.org)

Typing `compile` inside can be used to validate all the code is in place.

* PostgreSQL - v9.6.14 (https://www.postgresql.org)

It is assumed that there is a PostgreSQL database server installed.  On this instance, the table `schema` should exist in the database that will be used for experiments. This table has columns `tablename`, `fieldname`, `key`, `varfree` and each entry `(t,f,k,v)` represents the fact that table `t` has field `f` which is a key (if `k=true`) or value (if `k=false`), and it can contain variables (if `v=false`) or not (if `v=true`).  Tables and fields not mentioned in `schema` will be ignored by the system.

* Pentaho Data Integration (a.k.a. Kettle) - v9.1 (https://sourceforge.net/projects/pentaho)

You can start the tool by executing "spoon.sh" (or "Spoon.bat" if you have Windows in your personal computer). Be sure that you are using Java JRE 8 for running the PDI tool (set up JAVA_HOME to this version). 
  
# Building

1. Create a database in PostgreSQL.
   1. Create table ``schema`` inside (this is done automatically on running the ETL flow).

      ```SQL
      CREATE TABLE schema (
   	   tablename text NULL,
   	   fieldname text NULL,
      	"key" bool NULL,
	      varfree bool NULL DEFAULT false
      );
      ```
   1. Create the auxiliary SQL funcions executing the scripts in the folder ``SQLFunctions`` (there is a different SQL script for each implementation of s-tables).
1. Launch the ETL flow ``UseCase_COVID\covid_world.kjb`` in Kettle to fill the tables in ``UseCase_COVID\DatabaseeSchema.png``
1. Type `sbt` in the command line in this directory to enter the compiler. Once inside SBT, you can simply type `compile` to see all the code is in place and ready.


# Running

Inside SBT, you can run the experiments by typing:
```
runMain COVIDDeaths <hostname> <dbname> <user> <password> <implementation>
```
Where `<hostname>`, `<dbname>`, `<user>` and `<password>` are the parameters to connect to the database created above.
`<hostname>` is the host name of the database, `<dbname>` is the name of a database hosted on a PostgreSQL instance (`<hostname>`:5432) and `<user>` and `<password>` are credentials for a user having access to `<dbname>`.  
Finally, `<implementation>` can be either "partitioning" to use the normalized implementation of s-tables or "nf2_sparsev" to use the Non-First Normal Form implementation using sparse vectors (see more details bellow).

This basically loads the s-tables introducing error variables in the original data, calls the solver to find the solution of the algebraic queries for all the countries and shifts, and cleans up the database.

# Code structure

The code is structured in three different classes that can be easily composed to achieve the desired results.
Most of the top-level executable files also expose their scriptable functionality as a Scala method, so can be scripted from another Scala program.  
This approach has potential advantages over using shell / batch scripting:
- Portability
- Avoids multiple JVM restarts and (potentially) avoids creating numerous DB connections
- Avoids need to repeatedly enter connection parameters/encoding

## Loader

The `Loader` class offers an entry point that creates a symbolic version of a table, replacing NULL values with variables and optionally adding an error term for minimization.  We adopt the convention that null values are represented by variables starting with "_ " and these do not contribute to the cost of a solution, while all other variables do contribute.

The loader can use any encoding (see available encoders below) and works one table at a time. 
It basically creates some views in the database that add the desired variables. 
It firstly requires a database connection and the next one is the name of the table to load, which must be listed in the schema table of the database. 
Then, the encoding must be provided and finally a boolean indicating the view must be created or dropped.

It can also be used standalone as follows:
```
scala -cp "XXX.jar" Loader <hostname> <dbname> <user> <password> <tablename> <encoding> <cleanup>
```
The first four arguments are the usual database connection, and the fifth one is the table to load.
The optional `<encoding>` parameter selects which encoding to use (default is `partitioning`) and the `<cleanup>` indicates if the view is created or dropped.

## SolverWithoutViews

This is the class doing all the actual job through the method `solve`, which receives a database connector, an algebraic expression over s-tables and the encoding being used. 
Internally, it generates a linear/quadratic programming problem which is then solved by OSQP.
The corresponding Python code is stored in a temporary file `foo.py`.
The resulting valuation, and optimization distance if available, is printed out.

### Implemented Operations
Implemented algebraic operations are:
* Selection over key-attributes: `r(<attr> <comp> '<value>')`
* Projection of value-attributes: `r[<attr>(,<attr>)+]`
* Projection-away: `r[^<attr>(,<attr>)+]`
* Join: `r JOIN s`
* Union: `r UNION s`
* Discriminated union: `r DUNION[<attr>] s`
* Renaming: `r{<attr> -> <attr>(,<attr> -> <attr>)*}`
* Derivation: `r{<attr>:=[<attr>|<value>] <op> [<attr>|<value>](,<attr>|<value>] <op> [<attr>|<value>])+}`
* Sum aggregation of value-attributes grouping by key-attributes: `r[<attr>(,<attr>)* SUM <attr>(,<attr>)*]`
* Coalescing by removing some key attributes: `r[COAL <attr>(,<attr>)*]`

## Encoders

Two different encodings of s-tables are implemented. They can be chosen at runtime by simple changing the parameter of the loader.

### Normalized

This encoding creates a view for all constant terms of the s-table and then one more view for each of its attributes.
The primary key of the latter is the primary key of the s-table plus a variable name.
Thus, each row of these tables contains one of the non-constant terms of the symbolic expressions in the s-table.

### Non-First Normal Form with sparse vectors

This encoding creates a view with the same primary key and attributes as the s-table, but the datatype of every symbolic attribute is a sparse vector.

# Other tools

There are other tools that are not necessary to reproduce the final experiments, but are useful to get insights in s-tables and see the potential of the algebra.

## Main
Try the following command:
```
scala -cp "XXX.jar" Main <hostname> <dbname> <user> <password> <implementation>
```
where `<hostname>` is the host name of the database, `<dbname>` is the name of a database hosted on a PostgreSQL
instance (`<hostname>`:5432) and `<user>` and
`<password>` are credentials for a user having access to `<dbname>`.
This should result in the catalog query being run on `<dbname>` and the results printed out.  
Subsequently, typing in a relational algebra query should yield the equivalent SQL being printed and the query being run on the database.  
The query will first be typechecked against the schema represented by the `schema` table, and if it is not well formed then an error results.

## Transformer

The `Transformer` class transforms a raw table by applying a Gaussian noise distortion to nonnull values, replacing value fields with NULL with some probability, and deleting entire rows with some probability.
To run, use the following command:
```
./run-transformer.sh <hostname> <dbname> <user> <password> <tablename> <sigma> <p_null> <p_delete>
```
where the first four arguments are the same as usual, `<sigma>` is the
standard deviation of the Gaussian noise, `<p_null>` is the
probability of a field being replaced with NULL and `<p_delete>` is
the probability of an entire row being deleted.

