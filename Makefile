
run::
	mvn clean install jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

test::
	mvn clean clover:setup test clover:aggregate clover:clover
