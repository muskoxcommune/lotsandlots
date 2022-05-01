#!/usr/bin/env/python3
import argparse
import datetime
import hindsight
import logging

MAX_LOTS_OBSERVED = 15
MAX_DAYS_WITH_10_OR_MORE_LOTS = 20
MIN_PROFITS_PER_QUARTER = 300

BALANCE_SHEET_TOTAL_ASSETS_KEY = 'totalAssets'
BALANCE_SHEET_TOTAL_LIABILITIES_KEY = 'totalLiabilities'
BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY = 'totalShareholderEquity'
CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY = 'capitalExpenditures'
CASHFLOW_DATA_OPERATING_CASHFLOW_KEY = 'operatingCashflow'
DATA_KEY = 'data'
DATE_KEY = 'date'
FISCAL_DATE_ENDING_KEY = 'fiscalDateEnding'
INCOME_DATA_COST_OF_REVENUE_KEY = 'costOfRevenue'
INCOME_DATA_EBITDA_KEY = 'ebitda'
INCOME_DATA_TOTAL_REVENUE_KEY = 'totalRevenue'
QUARTERLY_REPORTS_KEY = 'quarterlyReports'
SHOULD_TRADE_KEY = 'shouldTrade'
VALUE_KEY = 'value'

def prepare_business_data(loaded_data, template):
    column_names = list(template.keys())
    for name in column_names:
        template[name + 'Previous'] = []
    loaded_data_size = len(loaded_data[QUARTERLY_REPORTS_KEY])
    # Loaded data is sorted in descending order, so we iterate in reverse.
    for i in range(loaded_data_size)[::-1]:
        fiscal_quarter = loaded_data[QUARTERLY_REPORTS_KEY][i]
        for k in column_names:
            # Skip earliest date since we look at last two quarters.
            if i != (loaded_data_size - 1):
                template[k].append(fiscal_quarter[k])
                template[k + 'Previous'].append(loaded_data[QUARTERLY_REPORTS_KEY][i+1][k])
    data_size = len(template[FISCAL_DATE_ENDING_KEY])
    # All columns should be sized equally, abort if that's not the case.
    for k in template.keys():
        k_data_size = len(template[k])
        assert data_size == k_data_size, 'data_size: %s, k: %s, k_data_size: %s' % (data_size, k, k_data_size)
    return template

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('-e', '--evaluation-output', required=True, help='output file')
    argparser.add_argument('-s', '--stock-data', required=True, help='path ot stock data file')
    argparser.add_argument('-t', '--training-output', required=True, help='output file')

    argparser.add_argument('--balance-sheet-data', required=True, help='path to balance sheet data file')
    argparser.add_argument('--cashflow-data', required=True, help='path to cashflow data file')
    argparser.add_argument('--income-data', required=True, help='path to income statement data file')
    argparser.add_argument('--overview-data', required=True, help='path overview data file')

    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')
    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    loaded_balance_sheet_data = hindsight.load_data_to_dict(args.balance_sheet_data)
    loaded_cashflow_data = hindsight.load_data_to_dict(args.cashflow_data)
    loaded_income_data = hindsight.load_data_to_dict(args.income_data)
    loaded_stock_data = hindsight.load_data_to_dict(args.stock_data)

    balance_sheet_data_template = {
        FISCAL_DATE_ENDING_KEY: [],
        BALANCE_SHEET_TOTAL_ASSETS_KEY: [],
        BALANCE_SHEET_TOTAL_LIABILITIES_KEY: [],
        BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY: [],
    }
    balance_sheet_data = prepare_business_data(loaded_balance_sheet_data, balance_sheet_data_template)
    assert len(balance_sheet_data[FISCAL_DATE_ENDING_KEY]) > 1, \
        "not enough data: %s" % balance_sheet_data[FISCAL_DATE_ENDING_KEY]

    cashflow_data_template = {
        FISCAL_DATE_ENDING_KEY: [],
        CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY: [],
        CASHFLOW_DATA_OPERATING_CASHFLOW_KEY: [],
    }
    cashflow_data = prepare_business_data(loaded_cashflow_data, cashflow_data_template)
    assert len(cashflow_data[FISCAL_DATE_ENDING_KEY]) > 1, \
        "not enough data:%s" % cashflow_data[FISCAL_DATE_ENDING_KEY]

    income_data_template = {
        FISCAL_DATE_ENDING_KEY: [],
        INCOME_DATA_COST_OF_REVENUE_KEY: [],
        INCOME_DATA_EBITDA_KEY: [],
        INCOME_DATA_TOTAL_REVENUE_KEY: [],
    }
    income_data = prepare_business_data(loaded_income_data, income_data_template)
    assert len(income_data[FISCAL_DATE_ENDING_KEY]) > 1, \
        "not enough data:%s" % income_data[FISCAL_DATE_ENDING_KEY]

    # TODO: Dates must be consecutive
    # TODO: All rows must have numeric values
    assert len(set(balance_sheet_data[FISCAL_DATE_ENDING_KEY]).symmetric_difference(set(cashflow_data[FISCAL_DATE_ENDING_KEY]))) == 0, \
        "data mismatch:\n%s\n%s" % (balance_sheet_data[FISCAL_DATE_ENDING_KEY], cashflow_data[FISCAL_DATE_ENDING_KEY])
    assert len(set(balance_sheet_data[FISCAL_DATE_ENDING_KEY]).symmetric_difference(set(income_data[FISCAL_DATE_ENDING_KEY]))) == 0, \
        "data mismatch:\n%s\n%s" % (balance_sheet_data[FISCAL_DATE_ENDING_KEY], income_data[FISCAL_DATE_ENDING_KEY])

    business_data = {}
    business_data.update(balance_sheet_data)
    business_data.update(cashflow_data)
    business_data.update(income_data)

    column_headers = [SHOULD_TRADE_KEY] + list(business_data.keys())
    evaluation_rows = []
    training_rows = []
    for i in range(balance_sheet_data_size - 1): # Drop most recent quarter
        timeseries_data = hindsight.prepare_timeseries_data(
            loaded_stock_data,
            # Format: YYYY-MM-DD
            # We drop the DD and match with YYYY-MM
            '-'.join(balance_sheet_data[FISCAL_DATE_ENDING_KEY][i].split('-')[:2]), # This end date
            '-'.join(balance_sheet_data[FISCAL_DATE_ENDING_KEY][i+1].split('-')[:2]), # Next end date
        )
        should_trade = '1'
        lots, profits, stats = hindsight.run_simulation(timeseries_data)
        if sum(profits) < MIN_PROFITS_PER_QUARTER:
            should_trade = '0'
        if stats['max_lots_observed'] > MAX_LOTS_OBSERVED:
            should_trade = '0'
        if stats['num_lots_counters'][10] > MAX_DAYS_WITH_10_OR_MORE_LOTS:
            should_trade = '0'

        new_row = []
        for header in column_headers:
            if header == SHOULD_TRADE_KEY:
                new_row.append(should_trade)
            else:
                new_row.append(business_data[header][i])

        if i < (balance_sheet_data_size / 2):
            training_rows.append(new_row)
        else:
            evaluation_rows.append(new_row)

    with open(args.evaluation_output, 'w') as fd:
        fd.write(','.join(column_headers) + '\n')
        for row in evaluation_rows:
            fd.write(','.join(row) + '\n')

    with open(args.training_output, 'w') as fd:
        fd.write(','.join(column_headers) + '\n')
        for row in training_rows:
            fd.write(','.join(row) + '\n')
