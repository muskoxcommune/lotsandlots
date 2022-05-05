#!/usr/bin/env/python3
import argparse
import copy
import logging
import numpy as np
import os
import pandas as pd
import time

import hindsight

# Requires data exported using Yahoo Finance Plus.

SIMULATION_DURATION = 90 # Days
MAX_LOTS_OBSERVED = 15
MAX_DAYS_WITH_10_OR_MORE_LOTS = 20
MIN_PROFITS_PER_QUARTER = 300

AGE = '_Age'
CAPITAL_EXPENDITURE = 'CapitalExpenditure'
COST_OF_REVENUE = 'CostOfRevenue'
DATE = 'Date'
EBITDA = 'EBITDA'
ENTERPRISE_VALUE = 'EnterpriseValue'
HIGHEST_HIGH = str(SIMULATION_DURATION) + 'DayHighestHigh'
LOWEST_LOW = str(SIMULATION_DURATION) + 'DayLowestLow'
MARKET_CAP = 'MarketCap'
OFFSET_CLOSE = str(SIMULATION_DURATION) + 'DayOffsetClose'
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
            column_remap[c] = '%s-%s-%s' % (year, month, date) # Conform to stock data Date format
            data[c] = data[c].apply(lambda s: float(s)) # Convert strings to floats
    data.rename(columns=column_remap, inplace=True)
    data = data.set_index(DATE).T # Transpose columns and rows
    logging.info('Finished reading %s after %s seconds:\n%s', csv_file, time.time() - data_read_start_time, data)
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
        default to that. Not all information is available at the same granularity.
    """

    balance_sheet_data = None
    balance_sheet_data = load_highest_granularity_financial_data_from_csv(symbol, 'balance-sheet')
    assert balance_sheet_data is not None
    earliest_common_datetime = np.datetime64(balance_sheet_data.index[-1]) # Initial value

    cash_flow_data = None
    cash_flow_data = load_highest_granularity_financial_data_from_csv(symbol, 'cash-flow')
    assert cash_flow_data is not None
    earliest_cash_flow_date = np.datetime64(cash_flow_data.index[-1])
    if earliest_cash_flow_date > earliest_common_datetime:
        earliest_common_datetime = earliest_cash_flow_date

    income_statement_data = None
    income_statement_data = load_highest_granularity_financial_data_from_csv(symbol, 'financials')
    assert income_statement_data is not None
    earliest_income_statement_date = np.datetime64(income_statement_data.index[-1])
    if earliest_income_statement_date > earliest_common_datetime:
        earliest_common_datetime = earliest_income_statement_date

    valuation_data = None
    valuation_data = load_highest_granularity_financial_data_from_csv(symbol, 'valuation_measures')
    assert valuation_data is not None
    earliest_valuation_date = np.datetime64(valuation_data.index[-1])
    if earliest_valuation_date > earliest_common_datetime:
        earliest_common_datetime = earliest_valuation_date

    # Load stock data

    stock_data_csv_file = input_dir + '/' + symbol + '.csv'
    assert os.path.exists(stock_data_csv_file), 'could not find ' + stock_data_csv_file
    stock_data = hindsight.load_stock_data_from_csv(stock_data_csv_file)
    earliest_stock_date = np.datetime64(stock_data.index[0])
    if earliest_stock_date > earliest_common_datetime:
        earliest_common_datetime = earliest_stock_date

    stock_dividends_data = None
    stock_dividends_data_csv_file = input_dir + '/' + symbol + '_dividends.csv'
    if os.path.exists(stock_dividends_data_csv_file):
        stock_dividends_data = hindsight.load_stock_data_from_csv(stock_dividends_data_csv_file)

    stock_split_data = None
    stock_split_data_csv_file = input_dir + '/' + symbol + '_splits.csv'
    if os.path.exists(stock_split_data_csv_file):
        stock_split_data = hindsight.load_stock_data_from_csv(stock_split_data_csv_file)

    # Generate composite training data
    
    """ We start with a scaffold dictionary. This will be fleshed out and used to initialize
        a pandas DataFrame. We will use this DataFrame to create our training and evaluation
        data sets.
    """
    composite_data_dict = {
        DATE: [],
        SHOULD_TRADE: [],
        hindsight.CLOSE: [],
        HIGHEST_HIGH: [],
        LOWEST_LOW: [],
        OFFSET_CLOSE: [],
    }

    # Mapping of column to source data DataFrame for convenience when looking up data.
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

    """ We have to deal with inputs at varying intervals. Some data is available at annual
        intervals. Others are available at monthly, quarterly, or daily intervals. We want
        to try to use whatever we have at hand. To do this, we will maintain a mapping of last
        known values and the time when those values became public. At any instance in time we
        will try to recreate a snapshot of what was known at that time and attempt to normalize
        inputs using their age.
    """
    last_known = {}
    for column_name in financial_data.keys():
        """ Company financial data is sorted using dates in descending order so we want to walk
            from the earliest available date and stop at earliest_common_datetime. We stop there
            because that is the earliest point from which all data is available we we will begin 
            walking our data from there.
        """
        earliest_date_datetime = np.datetime64(financial_data[column_name].index[-1])
        for date in financial_data[column_name].index[::-1]:
            date_datetime = np.datetime64(date)
            if date_datetime <= earliest_common_datetime:
                earliest_date_datetime = date_datetime
            else:
                break
        earliest_date = np.datetime_as_string(earliest_date_datetime, unit='D'),
        last_known[column_name] = {
            DATE: earliest_date,
            'Value': float(financial_data[column_name].loc[earliest_date][column_name]),
        }
        # Initialize composite columns
        composite_data_dict[column_name] = []
        composite_data_dict[column_name + AGE] = []
    logging.debug('Initial last_known: %s', last_known)

    """ All source DataFrames are indexed by dates. To walk through our data, we initialize a
        cursor datetime object and incrementing one day at a time.
    """
    cursor_datetime = copy.deepcopy(earliest_common_datetime)

    """ We add 90 days to the earliest_common_datetime because we aggregate data over the past
        90 days. We subtract the same duration from the last available date because we need as
        many days of data to run our simulation.
    """
    simulation_duration_days = np.timedelta64(SIMULATION_DURATION, 'D')
    composite_data_earliest_datetime = earliest_common_datetime + simulation_duration_days
    last_cursor_datetime = np.datetime64(stock_data.index[-1]) - simulation_duration_days

    while cursor_datetime <= last_cursor_datetime:
        cursor_date = np.datetime_as_string(cursor_datetime, unit='D')
        cursor_date_is_trading_day = cursor_date in stock_data.index

        for column_name in composite_data_dict.keys():
            if column_name in financial_data:
                if cursor_date in financial_data[column_name].index:
                    """ Update last_known as information changes and document the date that the
                        changes occurred on.
                    """
                    last_known[column_name][DATE] = cursor_datetime
                    last_known[column_name]['Value'] = float(financial_data[column_name].loc[cursor_date][column_name])
                if cursor_datetime >= composite_data_earliest_datetime and cursor_date_is_trading_day:
                    composite_data_dict[column_name].append(last_known[column_name]['Value'])
                    composite_data_dict[column_name + AGE].append((cursor_datetime - last_known[column_name][DATE]) / np.timedelta64(1, 'D'))

        if cursor_datetime >= composite_data_earliest_datetime and cursor_date_is_trading_day:
            composite_data_dict[DATE].append(cursor_date)

            cursor_date_stock_data = stock_data.loc[cursor_date]
            composite_data_dict[hindsight.CLOSE].append(cursor_date_stock_data[hindsight.CLOSE])

            """ To extract aggregated data, we create a reverse cursor for each cursor_datetime
                and walk back simulation_duration_days.
            """
            reverse_cursor_datetime = copy.deepcopy(cursor_datetime)
            last_reverse_cursor_datetime = reverse_cursor_datetime - simulation_duration_days

            offset_close_price = None
            highest_observed_price = None
            lowest_observed_price = None
            while reverse_cursor_datetime >= last_reverse_cursor_datetime:
                reverse_cursor_date = np.datetime_as_string(reverse_cursor_datetime, unit='D')
                if reverse_cursor_date in stock_data.index:
                    reverse_cursor_date_stock_data = stock_data.loc[reverse_cursor_date]
                    offset_close_price = reverse_cursor_date_stock_data[hindsight.CLOSE]
                    if not highest_observed_price or highest_observed_price < reverse_cursor_date_stock_data[hindsight.HIGH]:
                        highest_observed_price = reverse_cursor_date_stock_data[hindsight.HIGH]
                    if not lowest_observed_price or lowest_observed_price > reverse_cursor_date_stock_data[hindsight.LOW]:
                        lowest_observed_price = reverse_cursor_date_stock_data[hindsight.LOW]

                reverse_cursor_datetime -= np.timedelta64(1, 'D')

            composite_data_dict[HIGHEST_HIGH].append(highest_observed_price)
            composite_data_dict[LOWEST_LOW].append(lowest_observed_price)
            composite_data_dict[OFFSET_CLOSE].append(offset_close_price)

            # Run hindsight simulation and use the outcome as label values for training.

            simulation_end_datetime = (cursor_datetime + simulation_duration_days)
            lots, profits, stats = hindsight.run_simulation(stock_data, cursor_date, np.datetime_as_string(simulation_end_datetime, unit='D'))

            should_trade = 1
            if sum(profits) < MIN_PROFITS_PER_QUARTER:
                should_trade = 0
            if stats['max_lots_observed'] > MAX_LOTS_OBSERVED:
                should_trade = 0
            if stats['num_lots_counters'][10] > MAX_DAYS_WITH_10_OR_MORE_LOTS:
                should_trade = 0
            composite_data_dict[SHOULD_TRADE].append(should_trade)

        cursor_datetime += np.timedelta64(1, 'D')

    composite_data = pd.DataFrame(composite_data_dict)
    composite_data = composite_data.set_index(DATE)
    pd.set_option('display.max_rows', None)

    """ To make the data ready for training, we just need to drop the date index, remove
        any duplicate entries, and shuffle.
    """
    #composite_data.reset_index(drop=True, inplace=True)
    #composite_data = composite_data.drop_duplicates()
    if args.debug:
        logging.debug('Training ready DataFrame:\n%s', composite_data)
    else:
        logging.info('Training ready DataFrame:\n%s', composite_data.head())

