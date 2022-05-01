#!/usr/bin/env/python3
import argparse
import hindsight
import json
import logging
import os
import sys

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

def prepare_balance_sheet_data(loaded_balance_sheet_data):
    balance_sheet_data = {
        FISCAL_DATE_ENDING_KEY: [],
        BALANCE_SHEET_TOTAL_ASSETS_KEY: [],
        BALANCE_SHEET_TOTAL_LIABILITIES_KEY: [],
        BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY: [],
    }
    while loaded_balance_sheet_data[QUARTERLY_REPORTS_KEY]:
        fiscal_quarter = loaded_balance_sheet_data[QUARTERLY_REPORTS_KEY].pop() # Data is sorted in descending order
        balance_sheet_data[FISCAL_DATE_ENDING_KEY].append(fiscal_quarter[FISCAL_DATE_ENDING_KEY])
        balance_sheet_data[BALANCE_SHEET_TOTAL_ASSETS_KEY].append(fiscal_quarter[BALANCE_SHEET_TOTAL_ASSETS_KEY])
        balance_sheet_data[BALANCE_SHEET_TOTAL_LIABILITIES_KEY].append(fiscal_quarter[BALANCE_SHEET_TOTAL_LIABILITIES_KEY])
        balance_sheet_data[BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY].append(fiscal_quarter[BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY])
    data_size = len(balance_sheet_data[FISCAL_DATE_ENDING_KEY])
    assert data_size == len(balance_sheet_data[BALANCE_SHEET_TOTAL_ASSETS_KEY])
    assert data_size == len(balance_sheet_data[BALANCE_SHEET_TOTAL_LIABILITIES_KEY])
    assert data_size == len(balance_sheet_data[BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY])
    return balance_sheet_data

def prepare_cashflow_data(loaded_cashflow_data):
    cashflow_data = {
        FISCAL_DATE_ENDING_KEY: [],
        CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY: [],
        CASHFLOW_DATA_OPERATING_CASHFLOW_KEY: [],
    }
    while loaded_cashflow_data[QUARTERLY_REPORTS_KEY]:
        fiscal_quarter = loaded_cashflow_data[QUARTERLY_REPORTS_KEY].pop() # Data is sorted in descending order
        cashflow_data[FISCAL_DATE_ENDING_KEY].append(fiscal_quarter[FISCAL_DATE_ENDING_KEY])
        cashflow_data[CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY].append(fiscal_quarter[CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY])
        cashflow_data[CASHFLOW_DATA_OPERATING_CASHFLOW_KEY].append(fiscal_quarter[CASHFLOW_DATA_OPERATING_CASHFLOW_KEY])
    data_size = len(cashflow_data[FISCAL_DATE_ENDING_KEY])
    assert data_size == len(cashflow_data[CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY])
    assert data_size == len(cashflow_data[CASHFLOW_DATA_OPERATING_CASHFLOW_KEY])
    return cashflow_data

def prepare_income_data(loaded_income_data):
    income_data = {
        FISCAL_DATE_ENDING_KEY: [],
        INCOME_DATA_COST_OF_REVENUE_KEY: [],
        INCOME_DATA_EBITDA_KEY: [],
        INCOME_DATA_TOTAL_REVENUE_KEY: [],
    }
    while loaded_income_data[QUARTERLY_REPORTS_KEY]:
        fiscal_quarter = loaded_income_data[QUARTERLY_REPORTS_KEY].pop() # Data is sorted in descending order
        income_data[INCOME_DATA_COST_OF_REVENUE_KEY].append(fiscal_quarter[INCOME_DATA_COST_OF_REVENUE_KEY])
        income_data[INCOME_DATA_EBITDA_KEY].append(fiscal_quarter[INCOME_DATA_EBITDA_KEY])
        income_data[FISCAL_DATE_ENDING_KEY].append(fiscal_quarter[FISCAL_DATE_ENDING_KEY])
        income_data[INCOME_DATA_TOTAL_REVENUE_KEY].append(fiscal_quarter[INCOME_DATA_TOTAL_REVENUE_KEY])
    data_size = len(income_data[FISCAL_DATE_ENDING_KEY])
    assert data_size == len(income_data[INCOME_DATA_COST_OF_REVENUE_KEY])
    assert data_size == len(income_data[INCOME_DATA_EBITDA_KEY])
    assert data_size == len(income_data[INCOME_DATA_TOTAL_REVENUE_KEY])
    return income_data

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

    balance_sheet_data = prepare_balance_sheet_data(loaded_balance_sheet_data)
    cashflow_data = prepare_cashflow_data(loaded_cashflow_data)
    income_data = prepare_income_data(loaded_income_data)

    fundamentals_data_size = len(balance_sheet_data[FISCAL_DATE_ENDING_KEY])
    assert fundamentals_data_size == len(cashflow_data[FISCAL_DATE_ENDING_KEY])
    assert fundamentals_data_size == len(income_data[FISCAL_DATE_ENDING_KEY])

    column_headers = [
        FISCAL_DATE_ENDING_KEY, # Included for human readablity
        SHOULD_TRADE_KEY,
        BALANCE_SHEET_TOTAL_ASSETS_KEY,
        BALANCE_SHEET_TOTAL_LIABILITIES_KEY,
        BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY,
        CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY,
        CASHFLOW_DATA_OPERATING_CASHFLOW_KEY,
        INCOME_DATA_COST_OF_REVENUE_KEY,
        INCOME_DATA_EBITDA_KEY,
        INCOME_DATA_TOTAL_REVENUE_KEY,
    ]
    evaluation_rows = []
    training_rows = []
    for i in range(fundamentals_data_size - 1):# Drop most recent quarter
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
        if i < (fundamentals_data_size / 2):
            training_rows.append([
                balance_sheet_data[FISCAL_DATE_ENDING_KEY][i],
                should_trade,
                balance_sheet_data[BALANCE_SHEET_TOTAL_ASSETS_KEY][i],
                balance_sheet_data[BALANCE_SHEET_TOTAL_LIABILITIES_KEY][i],
                balance_sheet_data[BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY][i],
                cashflow_data[CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY][i],
                cashflow_data[CASHFLOW_DATA_OPERATING_CASHFLOW_KEY][i],
                income_data[INCOME_DATA_COST_OF_REVENUE_KEY][i],
                income_data[INCOME_DATA_EBITDA_KEY][i],
                income_data[INCOME_DATA_TOTAL_REVENUE_KEY][i],
            ])
        else:
            evaluation_rows.append([
                balance_sheet_data[FISCAL_DATE_ENDING_KEY][i],
                should_trade,
                balance_sheet_data[BALANCE_SHEET_TOTAL_ASSETS_KEY][i],
                balance_sheet_data[BALANCE_SHEET_TOTAL_LIABILITIES_KEY][i],
                balance_sheet_data[BALANCE_SHEET_TOTAL_SHAREHOLDER_EQUITY_KEY][i],
                cashflow_data[CASHFLOW_DATA_CAPITAL_EXPENDITURES_KEY][i],
                cashflow_data[CASHFLOW_DATA_OPERATING_CASHFLOW_KEY][i],
                income_data[INCOME_DATA_COST_OF_REVENUE_KEY][i],
                income_data[INCOME_DATA_EBITDA_KEY][i],
                income_data[INCOME_DATA_TOTAL_REVENUE_KEY][i],
            ])

    with open(args.evaluation_output, 'w') as fd:
        fd.write(','.join(column_headers) + '\n')
        for row in evaluation_rows:
            fd.write(','.join(row) + '\n')

    with open(args.training_output, 'w') as fd:
        fd.write(','.join(column_headers) + '\n')
        for row in training_rows:
            fd.write(','.join(row) + '\n')
