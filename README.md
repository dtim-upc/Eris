<meta name="robots" content="noindex">

# Eris: Measuring discord among multidimensional data sources

# Setup

* Install Scala (https://www.scala-lang.org/download)  - v2.13 with
JVM 11 should work.

   If Scala is correctly installed, typing `scala` and then something like `1+1` at the resulting prompt should result in something like `val res0 : Int = 2` being printed.

* SBT (https://www.scala-sbt.org) should also be installed - 0.13.18
  or greater should work.

* It is assumed that there is a Postgres database server
  installed.  On this instance, the table `schema` should exist in the
  database that will be used for experiments.  This table has columns
  `tablename`, `fieldname`, `key` and each entry `(t,f,k)` represents
  the fact that table `t` has field `f` which is a key (if `k=true`)
  or value (if `k=false`).  Tables and fields not mentioned in
  `schema` will be ignored by the system.

   * The auxiliary SQL funcions must be created by executing the scripts in the folder ``SQLFunctions`` (there is a different SQL script for each implementation of s-tables).

* It is also assumed that Python 3 and libraries `numpy`, `scipy` and
  `osqp` are installed.  Tested with Python v3.6, numpy v1.18.4, scipy v1.5.2, and osqp v0.6.1; doing `pip install
  numpy scipy osqp` should suffice.
  
  Required libraries and drivers (including JDBC) are already in `lib` folder. 
  In Windows, ``python.bat`` must point to the Python implementation with OSQP.
  
# Building

If all of the above are installed, this should just work:
```
sbt compile
```
alternatively
```
make
```

# Loading raw data into the database

You can setup a database, create user credentials and create ordinary tables in PostgreSQL as usual. Then, you can fill in the tables using ETL as in ``UseCase_COVID`` (or fill any other table at will, and run your own experiments).
For Eris to
know about them, there should also be a table called `schema`, defined
as follows:
```
CREATE TABLE public.schema
(
  tablename text NOT NULL,
  fieldname text NOT NULL,
  key boolean NOT NULL,
  varfree boolean DEFAULT false,
  CONSTRAINT schema_pkey PRIMARY KEY (tablename, fieldname)
  )
```
A table T with key fields K1,...,Kn and value fields V1...Vm should be
represented with n tuples
(tablename=T,fieldname=Ki,key=True,varfree=false) and m tuples
(tablename=T,fieldname=Vi,key=True,varfree=b)
where b is true if the field never has any symbolic variables and
false otherwise.  (This information allows some simple optimizations.)
For already-defined tables, this information needs to be added
manually, but for tables created by Eris for example by the loader or
viewer utilities, these are added automatically.

# Running

Try the following command in UNIX:
```
sbt -J-Xmx4g Main <hostname> <dbname> <user> <password>
```
or equivalently
```
./run.sh <hostname> <dbname> <user> <password>
```
alternatively, in Windows, simply enter SBT and execute:
```
run main <hostname> <dbname> <user> <password>
```
where `<hostname>` is the host name of the database, `<dbname>` is the name of a database hosted on a PostgreSQL
instance (`<hostname>`:5432) and `<user>` and
`<password>` are credentials for a user having access to `<dbname>`.
This should result in the catalog query being run on `<dbname>` and
the results printed out.  Subsequently, typing in a relational algebra
query should yield the equivalent SQL being printed and the query
being run on the database.  The query will first be typechecked
against the schema represented by the `schema` table, and if it is not
well formed then an error results.

The code is structured in three different classes that can be easily composed to achieve the desired results.

## Loading raw views

The `Viewer` class offers an entry point that evaluates a query over
raw inputs and
stores it in the database as a new raw table.
```
./run-viewer.sh <hostname> <dbname> <user> <password> <spec>?
```
The first four arguments are the same as usual.  If the `<spec>`
argument is not provided, the viewer provides a
REPL that allows entering a view definition of the form `t := q`.  If
table `t` is already present in the schema then you will be prompted
whether to replace and overwrite it.

If `<spec>` is provided then it is treated as the filename of a
specification defining one or more views, each of which is executed in
order and stored in the database.  Later views can refer to
earlier ones.

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

## Transforming a raw table

The `Transformer` class transforms a raw table by applying a Gaussian
noise distortion to nonnull values, replacing value fields with NULL
with some probability, and deleting entire rows with some probability.
To run, use the following command:
```
./run-transformer.sh <hostname> <dbname> <user> <password> <tablename> <sigma> <p_null> <p_delete>
```
where the first four arguments are the same as usual, `<sigma>` is the
standard deviation of the Gaussian noise, `<p_null>` is the
probability of a field being replaced with NULL and `<p_delete>` is
the probability of an entire row being deleted.


## Loading symbolic tables

The `Loader` class offers an entry point that creates a symbolic
version of a table, replacing NULL values with variables and
optionally adding an error term for minimization.  We adopt the
convention that null values are represented by variables starting with
"_" and these do not contribute to the cost of a solution, while all
other variables do contribute.

The loader currently works one table
at a time.  It is used as follows:
```
./run-loader.sh <hostname> <dbname> <user> <password> <tablename> <encoding>? <cleanup>?
```
The first four arguments are the same as usual, while the final one is
the name of the table to load.  This table must be listed in the
schema table.  The optional `<encoding>` parameter selects which
encoding to use, either `partitioning`
or `nf2_sparsev`.  The optional `<cleanup>` flag performs cleanup of
previously loaded symbolic tables.

Currently, the loader creates virtual views to define the tables
representing symbolic tables, rather than materializing the data.
As already said, it offers two different encodings of s-tables that can be chosen at runtime by simple changing the corresponding parameter.

### Partitioning

This encoding creates a view for all constant terms of the s-table and then one more view for each of its attributes.
The primary key of the latter is the primary key of the s-table plus a variable name.
Thus, each row of these tables contains one of the non-constant terms of the symbolic expressions in the s-table.

### Non-First Normal Form with sparse vectors

This encoding creates a view with the same primary key and attributes as the s-table, but the datatype of every symbolic attribute is a sparse vector.

## Solving 

The `MaterializedSolver` class offers an entry point for finding a solution
relating a symbolic table to a raw table. At the moment this supports REPL
and CLI interaction, starting as follows:
```
./run-solver.sh <hostname> <dbname> <user> <password> (<encoding> <table>?)?
```
If the `<table>` parameter is supplied, then `<encoding>` also needs
to be supplied and the solver runs noninteractively on the given table
name and encoding.
If the table is not supplied then a REPL starts which accepts table
names. Solving using table name `t` assumes such that there is both a raw table
named `t` and a loaded symbolic table with the same root name and
schema, using the provided encoding (which defaults to
`partitioning`).
The two tables are loaded and traversed to generate a
linear/quadratic programming problem which is then solved by OSQP.
The resulting valuation, and optimization distance if available, is printed out.
 The optional `<encoding>` parameter selects which
encoding to use, either `partitioning`
or `nf2_sparsev`.


The `VirtualSolver` class performs the equivalent solving but uses
virtual views to define the symbolic query results as well as to
extract the system of equations that will be sent to the solver.  It
also incorporates many optimizations to enable processing large
results in a streaming fashion without running out of
memory/overflowing the stack.  It does not offer an interactive mode,
but examples of its use within scripts are shown in `SolveAll.scala`
and `COVIDDeaths.scala`.

# Makefile and building JAR file

The `Makefile` now automates the process of building using SBT
and comes with a script `run.sh` which
can be run as follows:

```
make
run.sh <hostname> <dbname> <user> <password>
```


# Scripting

Most of the top-level executable files also expose their scriptable
functionality as a Scala method, so can be scripted from another Scala
program.  The file `Script.scala` shows an example of this, currently
relying on some external files where specifications are stored (but
these could also be stored in the script itself and parsed from string
form).

To run a Scala file that scripts actions in this way, do something
like:
```
./run-script.sh Script.scala <hostname> <dbname> <username> <password> <encoding>
```
This approach has potential advantages over using shell / batch
scripting:
- portability
- avoids multiple JVM restarts and (potentially) avoids creating
numerous DB connections
- avoids need to repeatedly enter connection parameters/encoding

# COVID use case

As a more complex example, we provide the analysis of two different sources of COVID19 data. 
For reproducability, all data is provided under `UseCase_COVID` folder.
Once the database is loaded with the corresponding tables, try the following command in UNIX:
```
sbt -J-Xmx4g COVIDDeaths <hostname> <dbname> <user> <password> <encoding>
```
or equivalently simply enter SBT and execute:
```
run COVIDDeaths <hostname> <dbname> <user> <password> <encoding>
```
This iterates over six different countries (namely NL, SE, DE, IT, ES, UK), and aligns the data available in JHU with those in Eurostats related to COVID cases and deaths.
For eight possible alignments of cases and deaths (between one and eight weeks), and each week between February 2020 and February 2021, it generates two rows for each country.
The first row corresponds to a query over data at both region and country levels, while the second only considers those only at country level.
Each one of these rows shows:
- KindOfQuery: Either considering regional data or only those at the country level.
- Shift: Between one and eight weeks for cases to be transformed into deaths.
- Country: One of NL, SE, DE, IT, ES, and UK.
- Week: Between fifth of 2020 and sixth of 2021.
- #Eq: Number of equations being generated by the query.
- #Vars: Overall number of variables (a.k.a. LUNs) being used in the equations.
- Eq. creation time: Time required by PostgreSQL to answer the query creating the equations.
- Solve time: Time required by OSQP in Python to solve the system of equations.
- Average squared error: Sum of the squared values found for all the variables.
