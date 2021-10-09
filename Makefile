
run::
	#mvn clean install jetty:run -Dconfig.file=$$HOME/lotsandlots.properties
	mvn clean install jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

install::
	mvn clean install
