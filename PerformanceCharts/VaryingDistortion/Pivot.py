import pyspark
from pyspark.sql.functions import col, median
import pandas

def run(s, list_q):
    # Obtain dataset
    raw = s.read.csv(r'input\results-varying-distortion.csv', header='false', inferSchema='true', sep=',').cache()
    print("File loaded!!!")
    for q in list_q:
        query = raw.where("_c3='"+q+"'"). \
            groupBy("_c4", "_c5"). \
            agg(median(col("_c6")).alias("median_c6")). \
            cache()
        q00 = query.where("_c5='0'").select(col("_c4").alias("x"), col("median_c6").alias("y00"))
        q05 = query.where("_c5='0.05'").select(col("_c4").alias("xPrime"), col("median_c6").alias("y05"))
        q10 = query.where("_c5='0.1'").select(col("_c4").alias("xPrime"), col("median_c6").alias("y10"))
        q20 = query.where("_c5='0.2'").select(col("_c4").alias("xPrime"), col("median_c6").alias("y20"))
        q40 = query.where("_c5='0.4'").select(col("_c4").alias("xPrime"), col("median_c6").alias("y40"))
        result = q00. \
            join(q05, [q00.x == q05.xPrime]). \
            join(q10, [q00.x == q10.xPrime]).\
            join(q20, [q00.x == q20.xPrime]).\
            join(q40, [q00.x == q40.xPrime]).\
            select("x", "y00", "y05", "y10", "y20", "y40").\
            sort(col("x"))
        result.show()
        # Create a CSV file with header
    #    result.write.csv(path="distPivot"+q+".csv", mode="overwrite", header=True)
        result.toPandas().to_csv("output\distPivot_T"+q[1]+".csv", header=True, index=False)
        print("CSV written!!!")

