import pyspark
from pyspark.sql.functions import col
from pyspark.sql.functions import col, avg, stddev, abs, greatest, min, max, median, collect_list, array_max, array_min
from pyspark.sql.functions import asc, desc
from pyspark.sql.types import StructType, StructField, StringType, IntegerType, DoubleType
import pandas

schema = StructType([
    StructField("_c0", StringType(), False),
    StructField("_c1", IntegerType(), False),
    StructField("_c2", StringType(), False),
    StructField("_c3", StringType(), False),
    StructField("_c4", StringType(), False),
    StructField("_c5", DoubleType(), False),
    StructField("_c6", DoubleType(), False),
    StructField("_c7", DoubleType(), False),
    StructField("_c8", DoubleType(), False)
    ])

def run(spark, s, q):
    # Obtain dataset
    raw = spark.read.csv(r'input\submitted_results-varying-variables.csv', schema=schema, header='false', sep=',').cache()
    print("File loaded!!!")
    query = raw.where("_c1="+s).where("_c3='"+q+"'").cache()
    q_p = query.where("_c2='partitioning'"). \
                withColumn("x", col("_c4")). \
                groupBy("x"). \
                agg(median(col("_c5")).alias("unknown_p"),
                    median(col("_c6")).alias("query_p"),
                    median(col("_c7")).alias("solve_p"),
                    median(col("_c8")).alias("total_p")
                    )
    q_nf2 = query.where("_c2='nf2_sparsev'"). \
                withColumn("xPrime", col("_c4")). \
                groupBy("xPrime"). \
                agg(median(col("_c5")).alias("unknown_nf2"),
                    median(col("_c6")).alias("query_nf2"),
                    median(col("_c7")).alias("solve_nf2"),
                    median(col("_c8")).alias("total_nf2")
                    )
    result = q_p. \
        join(q_nf2, [q_p.x == q_nf2.xPrime], "left_outer"). \
        select("x", "unknown_p", "query_p", "solve_p", "total_p", "unknown_nf2", "query_nf2", "solve_nf2", "total_nf2"). \
        sort(asc(col("x")))
    result.show()
    # Create a CSV file with header
#    result.write.csv(path="distPivot"+q+".csv", mode="overwrite", header=True)
    result.toPandas().to_csv(r"output\variying_variablesPivot_"+s+"_T"+q[1]+".csv", header=True, index=False)
    print("CSV written!!!")

