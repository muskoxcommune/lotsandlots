clean::
	mvn -B clean

run::
	mvn -B clean jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

test::
	mvn -B clean install test
