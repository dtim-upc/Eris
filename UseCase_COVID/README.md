<meta name="robots" content="noindex">

# Discordance measurement in COVID-19 data from JHU and EuroStats
We present a usecase of Eris based on the reported number of deaths due to COVID-19 in each country and region (as in JHU) and the overall number of deaths (as in EuroStats). The study is done per six countries, even if the sources are fully loaded into the database.

# Setup

* PostgreSQL - v9.6.14 (https://www.postgresql.org)

It is assumed that there is a PostgreSQL database server installed.  

* Pentaho Data Integration (a.k.a. Kettle) - v9.1 (https://sourceforge.net/projects/pentaho)

You can start the tool by executing "spoon.sh" (or "Spoon.bat" if you have Windows in your personal computer). Be sure that you are using Java JRE 8 for running the PDI tool (set up JAVA_HOME to this version). 
  
# Building

1. Create a database in PostgreSQL.
1. Call ``Spoon.bat`` or ``spoon.sh`` depending on the OS.

# Running

1. Launch the ETL flow ``UseCase_COVID\covid_world.kjb`` in Kettle to fill the tables in ``UseCase_COVID\DatabaseeSchema.png``
   1. Go to ``Edit->Set Environment Variables`` and provide the four values for the database connection (i.e., database, host, username and password).


# Code structure

The high level steps in the ETL are:
1. Create all the tables (without integrity constraints).
1. Load the dimensional information.
1. Declare the constraints of the dimensions
1. Load in parallel:
   1. JHU country data,
   1. JHU region data, and
   1. EuroStats data
1. Declare the constraints of the fact tables.
1. Update all the statistics of the database.

