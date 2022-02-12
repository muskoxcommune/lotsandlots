
run::
	mvn -B clean install jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

test::
	mvn -B clean clover:setup test clover:aggregate clover:clover
