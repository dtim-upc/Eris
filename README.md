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
Finally, `<implementation>` can be either "partitioning" to use the normalized implementation of s-tables or "nf2_sparsev" to use the Non-First Normal Form implementation using sparse vectors.


--------------------------------

Try the following command:
```
scala -cp "whoprov.jar" Main <hostname> <dbname> <user> <password> <implementation>
```
or equivalently
```
./run.sh <hostname> <dbname> <user> <password>
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

The loader currently uses the partitioning encoding and works one table
at a time.  It is used as follows:
```
scala -cp "whoprov.jar" Loader <hostname> <dbname> <user> <password> <tablename> <encoding>?
```
or equivalently
```
./run-loader.sh <hostname> <dbname> <user> <password> <tablename> <encoding>?
```
The first four arguments are the same as usual, while the final one is
the name of the table to load.  This table must be listed in the
schema table.  The optional `<encoding>` parameter selects which
encoding to use, either `multi`
or `pivoting`.


## Evaluating symbolic queries

The `Evaluator` class offers an entry point for evaluating a symbolic
query, assuming all of the tables mentioned in the query have been
loaded in symbolic form.  At the moment this supports REPL or noninteractive
interaction, starting as follows:
```
./run-evaluator.sh <hostname> <dbname> <user> <password> (<encoding> <spec>?)?
```
If the `<spec>` argument is omitted, a REPL starts and accepts view definition steps of the form `v := q` where `v`
is a new table name and `q` is a query.  Note that a table named `v`
will not be created directly, instead the query results will be
represented as one or more tables encoding the result.  The evaluator
is **totally unsafe** so using a view table name (or base table name) that
has an encoding already present in the database will result in existing data being
overwritten.  (Base tables should not be overwritten as long as their
names don't collide with encoding table names, but their accompanying
encoded tables may be.)  The optional `<encoding>` parameter selects which
encoding to use, either `partitioning`,
 `pivoting`, `nf2` or `nf2_sparsev`.

If a `<spec>` argument is provided then it is a filename and a specifiation is read in from
the named file and executed, just as for `Viewer`. Implemented algebraic operations are:
* Selection over key-attributes: `r(<attr> <comp> '<value>')`
* Projection of value-attributes: `r[<attr>(,<attr>)+]`
* Projection-away: `r[^<attr>(,<attr>)+]`
* Join: `r JOIN s`
* Union: `r UNION s`
* Discriminated union: `r DUNION[<attr>] s`
* Renaming: `r{<attr> -> <attr>(,<attr> -> <attr>)*}`
* Derivation: `r{<attr>:=[<attr>|<value>] <op> [<attr>|<value>](,<attr>|<value>] <op> [<attr>|<value>])+}`
* Sum aggregation of value-attributes grouping by key-attributes: `r[<attr>(,<attr>)* SUM <attr>(,<attr>)*]`
* Coalescing by removing some key attributes: `r[COAL <attr>(,<attr>)*]

## Solving 

The `Solver` class offers an entry point for finding a solution
relating a symbolic table to a raw table. At the moment this supports REPL
and CLI interaction, starting as follows:
```
./run-solver.sh <hostname> <dbname> <user> <password> (<encoding> <table>?)?
```
If the `<table>` parameter is supplied, then `<encoding>` also needs
to be supplied and the solver runs noninteractively on the given table
naem and encoding.
If the table is not supplied then a REPL starts which accepts table
names. Solving using table name `t` assumes such that there is both a raw table
named `t` and a loaded symbolic table with the same root name and
schema, using the provided encoding (which defaults to
`partitioning`).
The two tables are loaded and traversed to generate a
linear/quadratic programming problem which is then solved by OSQP.
The resulting valuation, and optimization distance if available, is printed out.
 The optional `<encoding>` parameter selects which
encoding to use, either `multi`
or `pivoting`.

TODO: the solver just considers one table at a time instead of
multiple ones.  This is easy to handle just by processing the same
view specification as used by the evaluator.

TODO: the solver requires the raw table and symbolic table to have the
same name.  We can work around this by copying/renaming tables for
now.

TODO: the raw table loading and coalescing is done in a bulk way, so
large tables will probably not work.  (Large tables that have many
variables will probably generate linear programming instances that are
too large anyway, so we may want to do some postprocessing to break
the resulting system down into independent subproblems anyway.)

# Makefile and building JAR file

The `Makefile` now automates the process of building a JAR file
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
