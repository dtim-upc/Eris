import os
import sys

from pyspark import SparkConf
from pyspark.sql import SparkSession

import Pivot

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
        .appName("Pivoting") \
        .getOrCreate()

    # Create the session
    spark = SparkSession.builder \
        .config(conf=conf) \
        .getOrCreate()

    for s in ['1000']:
        for q in ['q1', 'q2', 'q3', 'q4', 'q5']:
            Pivot.run(spark, s, q)
