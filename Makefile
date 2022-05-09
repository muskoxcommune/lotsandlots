
run::
	mvn -B clean jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

test::
	mvn -B clean test

workstation::
	source ~/miniforge3/bin/activate
	conda install -c apple tensorflow-deps
	python -m pip install matplotlib pandas scikit-learn tensorflow-macos

