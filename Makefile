
company_data::
	bash bin/fetch_data_for_company.sh -a $(API_KEY) -o tmp -s $(SYMBOL)

run::
	mvn -B clean jetty:run -Dconfig.file=$$HOME/lotsandlots.conf

test::
	mvn -B clean test

training_data:: company_data
	python bin/training_data_generator.py -s tmp/stock/$(SYMBOL).json \
		--balance-sheet-data tmp/balance_sheet/$(SYMBOL).json \
		--cashflow-data tmp/cashflow/$(SYMBOL).json \
		--evaluation-output tmp/modeling/eval/$(SYMBOL).csv \
		--income-data tmp/income/$(SYMBOL).json \
		--overview tmp/overview/$(SYMBOL).json \
		--training-output tmp/modeling/train/$(SYMBOL).csv
