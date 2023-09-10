import os
import sys

from pyspark import SparkConf
from pyspark.sql import SparkSession

import Preprocess

#Path to the resources folder of the project, use the full path on Windows
HADOOP_HOME = r"C:\Users\aabello\PycharmProjects\SparkExamples\hadoop_home"
#Use the full path on Windows for PYSPARK_PYTHON and PYSPARK_DRIVER_PYTHON, for instance: r"C:\SOFT\Python3\python.exe"
PYSPARK_PYTHON = r"C:\Program Files\Python310\python.exe"
PYSPARK_DRIVER_PYTHON = r"C:\Program Files\Python310\python.exe"

if __name__== "__main__":
    os.environ["HADOOP_HOME"] = HADOOP_HOME
    sys.path.append(HADOOP_HOME + "\\bin")
    os.environ["PYSPARK_PYTHON"] = PYSPARK_PYTHON
    os.environ["PYSPARK_DRIVER_PYTHON"] = PYSPARK_DRIVER_PYTHON

    conf = SparkConf()  # create the configuration

    ss = SparkSession.builder \
        .config(conf=conf) \
        .master("local") \
        .appName("Preprocessing") \
        .getOrCreate()

    # Create the session
    spark = SparkSession.builder \
        .config(conf=conf) \
        .getOrCreate()
    print(spark.version)

    print("eval_time")
    Preprocess.eval_time(spark, ['3all'], ['q1', 'q2', 'q3', 'q4', 'q5', 'q6', 'q7'])
#    Preprocess.eval_time(spark, ['0none', '1half', '2onlyr',], ['q1'])
    print("eqgen_time")
    Preprocess.eqgen_time(spark, ['3all'], ['q1', 'q2', 'q3', 'q4', 'q5'])
#    Preprocess.eqgen_time(spark, ['0none', '1half', '2onlyr',], ['q1'])
    print("overall_time_distribution")
    Preprocess.overall_time_distribution(spark, ['3all'], ['100', '1000', '10000'])
    print("osqp_coincidence")
    Preprocess.osqp_coincidence(spark, ['3all'])
