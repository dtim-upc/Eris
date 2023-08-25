ECHO -------------------------
ECHO Running viewer ...
ECHO -------------------------
@CALL run-viewer.bat %1 %2 %3 %4 copy.spec

ECHO -------------------------
ECHO Running transformers ...
ECHO -------------------------
@CALL run-transformer.bat %1 %2 %3 %4 r2 1.0 0.1 0.5
@CALL run-transformer.bat %1 %2 %3 %4 s2 1.0 0.1 0.5

ECHO -------------------------
ECHO Running loaders ...
ECHO -------------------------
@CALL run-loader.bat %1 %2 %3 %4 r2 %5
@CALL run-loader.bat %1 %2 %3 %4 s2 %5

ECHO -------------------------
ECHO Running viewer ...
ECHO -------------------------
@CALL run-viewer.bat %1 %2 %3 %4 t.spec

ECHO -------------------------
ECHO Running evaluator ...
ECHO -------------------------
@CALL run-evaluator.bat %1 %2 %3 %4 %5 t2.spec

ECHO -------------------------
ECHO Running solver ...
ECHO -------------------------
@CALL run-solver.bat %1 %2 %3 %4 %5 t

