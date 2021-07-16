all: whoprov.jar

whoprov.jar: *.MF *.scala
	sbt compile

clean:
	./clean.sh
	rm -f whoprov.jar
