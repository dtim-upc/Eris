import pyspark
from pyspark.sql.functions import col, avg, stddev, abs, greatest, min, max, median, regexp_replace
from pyspark.sql.functions import asc, desc
from pyspark.sql.types import StructType, StructField, StringType, IntegerType, DoubleType
import pandas


schema = StructType([
    StructField("_c0", StringType(), False),
    StructField("_c1", IntegerType(), False),
    StructField("_c2", StringType(), False),
    StructField("_c3", StringType(), False),
    StructField("_c4", DoubleType(), True),
    StructField("_c5", DoubleType(), True),
    StructField("_c6", DoubleType(), True),
    StructField("_c7", DoubleType(), True)
    ])


def eval_time(s, list_f, list_q):
    process_query_time(s, list_f, list_q, "eval", "4")


def eqgen_time(s, list_f, list_q):
    process_query_time(s, list_f, list_q, "solve", "5")


# First execution time looks usually higher than the others, better to take the median
def process_query_time(s, list_f, list_q, condition, column):
    for f in list_f:
        # Obtain dataset
        raw = s.read.csv("input\\results-scaling-"+f+".csv", schema=schema, header='false', sep=',')
        print("File "+f+" loaded!!!")
        raw.printSchema()
        filtered = raw.where("_c0='"+condition+"'")
        grouped = filtered.groupBy(col("_c3").alias("query"), col("_c1").alias("scale"), col("_c2").alias("encoding"))
        aggregated = grouped.agg(avg(col("_c"+column)).alias("avg"), stddev(col("_c"+column)).alias("stdev"), median(col("_c"+column)).alias("median"), min(col("_c"+column)).alias("min"), max(col("_c"+column)).alias("max")).cache()
        partitioning = aggregated.where("encoding='partitioning'").\
            select("query", "scale", col("avg").alias("avg_partitioning"), col("stdev").alias("stdev_partitioning"), col("median").alias("median_partitioning"), col("min").alias("min_partitioning"), col("max").alias("max_partitioning"))
        nf2 = aggregated.where("encoding='nf2_sparsev'").\
            select("query", "scale", col("avg").alias("avg_nf2"), col("stdev").alias("stdev_nf2"), col("median").alias("median_nf2"), col("min").alias("min_nf2"), col("max").alias("max_nf2"))
        ready = partitioning. \
            join(nf2, ["query", "scale"]).cache()
        for q in list_q:
            result = ready.where("query='"+q+"'").\
                select("scale", "avg_partitioning", "stdev_partitioning", "median_partitioning", "min_partitioning", "max_partitioning", "avg_nf2", "stdev_nf2", "median_nf2", "min_nf2", "max_nf2").\
                sort(asc("scale"))
            result.show()
            if condition == 'solve':
                q = 'T'+q[1]
            # Create a CSV file with header
            result.toPandas().to_csv("output\\"+condition+"_"+q+"_"+f+".csv", header=True, index=False)
            print("CSV "+condition+"_"+q+"_"+f+" written!!!")


def overall_time_distribution(s, list_f, list_s):
    for f in list_f:
        # Obtain dataset
        raw = s.read.csv("input\\results-scaling-" + f + ".csv", schema=schema, header='false', sep=',')
        print("File " + f + " loaded!!!")
        filtered = raw.where("_c0='solve'")
        fixed = filtered.withColumn("query", regexp_replace("_c3", 'q', 'T'))
        grouped = fixed.groupBy(col("query"), col("_c1").alias("scale"), col("_c2").alias("encoding"))
        aggregated = grouped.agg(avg("_c5").alias("avg_eqgen"), avg("_c6").alias("avg_osqp"))
        ready = aggregated.\
            withColumn('percent_eqgen', aggregated.avg_eqgen/(aggregated.avg_eqgen+aggregated.avg_osqp)).\
            withColumn('percent_osqp', aggregated.avg_osqp/(aggregated.avg_eqgen+aggregated.avg_osqp)).\
            cache()

        for scale in list_s:
            for enc in ["partitioning", "nf2_sparsev"]:
                result = ready.where("encoding='"+enc+"' AND scale="+scale+"").\
                    select("query", "percent_eqgen", "percent_osqp").\
                    sort(asc("query"))
                result.show()
                # Create a CSV file with header
                result.toPandas().to_csv("output\\"+scale+"_"+enc+"_"+f+".csv", header=True, index=False)
                print("CSV "+scale+"_"+enc+"_"+f+" written!!!")


# OSQP should coincide independently of the encoding
def osqp_coincidence(s, list_f):
    for f in list_f:
        # Obtain dataset
        raw = s.read.csv("input\\results-scaling-" + f + ".csv", schema=schema, header='false', sep=',')
        print("File " + f + " loaded!!!")
        filtered = raw.where("_c0='solve'")
        grouped = filtered.groupBy(col("_c3").alias("query"), col("_c1").alias("scale"), col("_c2").alias("encoding"))
        aggregated = grouped.agg(avg("_c6").alias("avg_osqp")).cache()
        partitioning = aggregated.where("encoding='partitioning'").withColumnRenamed("avg_osqp", "partitining_avg_osqp")
        nf2_sparsev = aggregated.where("encoding='nf2_sparsev'").withColumnRenamed("avg_osqp", "nf2_sparsev_avg_osqp")
        ready = partitioning.\
            join(nf2_sparsev, ["query", "scale"])

        result = ready.\
            select("query", "scale", "partitining_avg_osqp", "nf2_sparsev_avg_osqp").\
            withColumn("difference", abs(ready.partitining_avg_osqp-ready.nf2_sparsev_avg_osqp)/greatest(ready.partitining_avg_osqp, ready.nf2_sparsev_avg_osqp)).\
            sort(asc("query"), asc("scale"))
        #result.show()
        # Create a CSV file with header
        result.toPandas().to_csv("output\\osqp_comparison_"+f+".csv", header=True, index=False)
        print("CSV "+f+" written!!!")

