
run::
	mvn -B clean jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

test::
	mvn -B clean test
