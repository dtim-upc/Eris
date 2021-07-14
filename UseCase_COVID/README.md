<meta name="robots" content="noindex">

# Discordance measurement using COVID-19 data from JHU and EuroStats
We present a usecase of Eris based on the reported number of deaths due to COVID-19 in each country and region (as in [JHU](https://github.com/CSSEGISandData/COVID-19/tree/master/csse_covid_19_data/csse_covid_19_time_series)) and the overall number of deaths (as in [EuroStats](https://ec.europa.eu/eurostat/databrowser/view/demo_r_mwk2_ts/default/table?lang=en)). The study is done per six countries (NL,SE, DE,IT,ES,UK), even if the sources are fully loaded into the database.

To facilitate the integration of the sources, we used the [NUTS](https://ec.europa.eu/eurostat/web/nuts/background) as geographical master data and [COVIDData repository](https://github.com/coviddata/coviddata) as a surrogate to the regional data in JHU.

All the used data as downloaded from the corresponding source (in CSV format) is available in ``COVID data`` (files are named with the source, date like ``yymmdd``, and the data contained being either cases or deaths). There is also an empty subfolder ``Error logs`` required for the ETLs to leave log files while running.

# Setup

* PostgreSQL - v9.6.14 (https://www.postgresql.org)

It is assumed that there is a PostgreSQL database server installed.  

* Pentaho Data Integration (a.k.a. Kettle) - v9.1 (https://sourceforge.net/projects/pentaho)

You can start the tool by executing "spoon.sh" (or "Spoon.bat" if you have Windows in your personal computer). Be sure that you are using Java JRE 8 for running the PDI tool (set up JAVA_HOME to this version). 

* GNUPlot - v5.2 (http://www.gnuplot.info)
 
This is only required to generate the charts as in the paper.
 
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

# Results analysis

The testing scala program (whose output is in ``Charts\COVIDErrorData.csv``) prints in the standard output the summary of every entity being coalesced (resulting in an independent system of equations). Thus, every printed row contains:
* KindOfQuery: Whether it includes regional data or only those at the country level.
* Shift: In case of dealing with regional data, this indicates the shift used to align cases and deaths.
* Country: Identifier of the country.
* Week: Identifier of the week.
* #Eq: Number of equations in the system.
* #Vars: Overall number of variables in the system of equations.
* Eq. creation time: Time taken by the system to create all the equations.
* Solve time: Time taken by the system to solve the system of equations.
* Average squared error: Measure minimized on solving the system of equations.

These data can be automatically loaded into a dynamic table of the Excel file ``COVIDErrorAnalysis.xlsx`` through the MSExcel query mechanims, by simply ``Update All`` button in the ``Data`` tab. From there, any data can be manually selected and copied either to other tab or an independent CSV file for further processing with GNUPlot.

