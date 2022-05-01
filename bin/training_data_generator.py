#!/usr/bin/env/python3
import argparse
import logging
import numpy as np
import os
import pandas as pd
import time

import hindsight

# Requires data exported using Yahoo Finance Plus.

MAX_LOTS_OBSERVED = 15
MAX_DAYS_WITH_10_OR_MORE_LOTS = 20
MIN_PROFITS_PER_QUARTER = 300

AGE = '_Age'
CAPITAL_EXPENDITURE = 'CapitalExpenditure'
COST_OF_REVENUE = 'CostOfRevenue'
DATE = 'Date'
EBITDA = 'EBITDA'
ENTERPRISE_VALUE = 'EnterpriseValue'
MARKET_CAP = 'MarketCap'
OPERATING_CASH_FLOW = 'OperatingCashFlow'
SHOULD_TRADE = 'ShouldTrade'
TOTAL_ASSETS = 'TotalAssets'
TOTAL_EQUITY = 'TotalEquityGrossMinorityInterest'
TOTAL_LIABILITIES = 'TotalLiabilitiesNetMinorityInterest'
TOTAL_REVENUE = 'TotalRevenue'

def load_financial_data_from_csv(csv_file):
    data_read_start_time = time.time()
    data = pd.read_csv(csv_file)
    if 'ttm' in data:
        data.pop('ttm') # Drop ttm column (trailing twelve months)
    data['name'] = data['name'].apply(lambda n: n.strip()) # Metric names can have white spaces
    data = data.fillna(0) # Replace NaN with 0
    data = data.replace(',', '', regex=True) # Numbers in CSV are serialized as strings with commas
    column_remap = {}
    for c in data.columns:
        if c == 'name':
            column_remap[c] = DATE
        else:
            month, date, year = c.strip().split('/')
            column_remap[c] = np.datetime64('%s-%s-%s' % (year, month, date)) # Conform to stock data Date format
            data[c] = data[c].apply(lambda s: float(s)) # Convert strings to floats
    data.rename(columns=column_remap, inplace=True)
    data = data.set_index(DATE).T # Transpose columns and rows
    logging.debug('Finished reading %s after %s seconds:\n%s', csv_file, time.time() - data_read_start_time, data)
    return data

def load_highest_granularity_financial_data_from_csv(symbol, filename_suffix):
    data = None
    annual_csv_file = input_dir + '/' + symbol + '_annual_' + filename_suffix + '.csv'
    monthly_csv_file = input_dir + '/' + symbol + '_monthly_' + filename_suffix + '.csv'
    quarterly_csv_file = input_dir + '/' + symbol + '_quarterly_' + filename_suffix + '.csv'
    if os.path.exists(monthly_csv_file):
        data = load_financial_data_from_csv(monthly_csv_file)
    elif os.path.exists(quarterly_csv_file):
        data = load_financial_data_from_csv(quarterly_csv_file)
    elif os.path.exists(annual_csv_file):
        data = load_financial_data_from_csv(annual_csv_file)
    return data

def load_stock_data_from_csv(csv_file):
    stock_data_read_start_time = time.time()
    stock_data = pd.read_csv(csv_file)
    stock_data[DATE] = stock_data[DATE].apply(lambda s: np.datetime64(s))
    stock_data = stock_data.set_index(DATE)
    logging.debug('Finished reading %s after %s seconds:\n%s',
        csv_file, time.time() - stock_data_read_start_time, stock_data)

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-i', '--input-dir', required=True, help='path to input directory')
    argparser.add_argument('-o', '--output-dir', required=True, help='path to output directoryfile')
    argparser.add_argument('-s', '--symbol', required=True, help='stock symbol')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    input_dir = args.input_dir.rstrip('/')
    symbol = args.symbol.upper()

    assert os.path.isdir(input_dir), input_dir + ' is not a directory'

    # Load financial data
    """ We currently load the highest granularity data. In the future, we may not want to
        default to that. No all information is available at the same granularity.
    """

    balance_sheet_data = None
    balance_sheet_data = load_highest_granularity_financial_data_from_csv(symbol, 'balance-sheet')
    assert balance_sheet_data is not None

    cash_flow_data = None
    cash_flow_data = load_highest_granularity_financial_data_from_csv(symbol, 'cash-flow')
    assert cash_flow_data is not None

    income_statement_data = None
    income_statement_data = load_highest_granularity_financial_data_from_csv(symbol, 'financials')
    assert income_statement_data is not None

    valuation_data = None
    valuation_data = load_highest_granularity_financial_data_from_csv(symbol, 'valuation_measures')
    assert valuation_data is not None

    financial_data = {
        CAPITAL_EXPENDITURE: cash_flow_data,
        COST_OF_REVENUE: income_statement_data,
        EBITDA: income_statement_data,
        ENTERPRISE_VALUE: valuation_data,
        MARKET_CAP: valuation_data,
        OPERATING_CASH_FLOW: cash_flow_data,
        TOTAL_ASSETS: balance_sheet_data,
        TOTAL_EQUITY: balance_sheet_data,
        TOTAL_LIABILITIES: balance_sheet_data,
        TOTAL_REVENUE: income_statement_data,
    }

    # Load stock data

    stock_data_csv_file = input_dir + '/' + symbol + '.csv'
    assert os.path.exists(stock_data_csv_file), 'could not find ' + stock_data_csv_file
    stock_data = load_stock_data_from_csv(stock_data_csv_file)

    stock_dividends_data = None
    stock_dividends_data_csv_file = input_dir + '/' + symbol + '_dividends.csv'
    if os.path.exists(stock_dividends_data_csv_file):
        stock_dividends_data = load_stock_data_from_csv(stock_dividends_data_csv_file)

    stock_split_data = None
    stock_split_data_csv_file = input_dir + '/' + symbol + '_splits.csv'
    if os.path.exists(stock_split_data_csv_file):
        stock_split_data = load_stock_data_from_csv(stock_split_data_csv_file)

    composite_data_template = {
        DATE: [],
        SHOULD_TRADE: [],
    }
    for column_name in financial_data.keys():
        composite_data_template[column_name] = []
        composite_data_template[column_name + AGE] = []

    composite_data = pd.DataFrame(composite_data_template)
    composite_data.set_index(DATE)
    logging.debug('Generated composite DataFrame:\n%s', composite_data)











