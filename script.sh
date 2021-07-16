#!/bin/sh

./run-viewer.sh $1 $2 $3 $4 copy.spec

./run-transformer.sh $1 $2 $3 $4 r2 1.0 0.1 0
./run-transformer.sh $1 $2 $3 $4 s2 1.0 0.1 0

./run-loader.sh $1 $2 $3 $4 r2 $5
./run-loader.sh $1 $2 $3 $4 s2 $5

./run-viewer.sh $1 $2 $3 $4 t.spec

./run-evaluator.sh $1 $2 $3 $4 $5 t2.spec

./run-solver.sh $1 $2 $3 $4 $5 t

