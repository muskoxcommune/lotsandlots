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
MAX_LOTS_OBSERVED = 20
MAX_DAYS_WITH_5_OR_MORE_LOTS = 60
MAX_DAYS_WITH_10_OR_MORE_LOTS = 10
MAX_DAYS_WITH_15_OR_MORE_LOTS = 5

AGE = '_Age'
DATE = 'Date'

SHOULD_TRADE = 'ShouldTrade' # Label for training

HIGH_P25 = str(SIMULATION_DURATION) + 'DayHighP25'
HIGH_P75 = str(SIMULATION_DURATION) + 'DayHighP75'
HIGHEST_HIGH = str(SIMULATION_DURATION) + 'DayHighestHigh'
LOW_P25 = str(SIMULATION_DURATION) + 'DayLowP25'
LOW_P75 = str(SIMULATION_DURATION) + 'DayLowP75'
LOWEST_LOW = str(SIMULATION_DURATION) + 'DayLowestLow'
VOLUME = 'Volume'
VOLUME_P25 = str(SIMULATION_DURATION) + 'DayVolumeP25'
VOLUME_P50 = str(SIMULATION_DURATION) + 'DayVolumeP50'
VOLUME_P75 = str(SIMULATION_DURATION) + 'DayVolumeP75'

ADVANCE_ALL_RETAIL_SALES = 'AdvanceAllRetailSales'
ADVANCE_BUILDING_MATERIALS_SALES = 'AdvanceBuildingMaterialsSales'
ADVANCE_CLOTHING_SALES = 'AdvanceClothingAndAccessorySales'
ADVANCE_FOOD_AND_DRINK_SALES = 'AdvanceFoodAndDrinkSales'
ADVANCE_FURNITURE_SALES = 'AdvanceFurnitureSales'
ADVANCE_GAS_STATION_SALES = 'AdvanceGasStationSales'
ADVANCE_GROCERY_SALES = 'AdvanceGrocerySales'
ADVANCE_HOBBY_SALES = 'AdvanceHobbySales'
ADVANCE_NONSTORE_SALES = 'AdvanceNonstoreSales'
ADVANCE_VEHICLE_AND_PARTS_SALES = 'AdvanceVehicleAndPartsSales'
CONSUMER_INFLATION_EXPECTATION = 'ConsumerInflationExpectation'
DURABLE_GOODS_ORDERS = 'DurableGoodsOrders'
FEDERAL_FUNDS_RATE = 'FederalFundsRate'
MEDIAN_CONSUMER_PRICE_INDEX = 'MedianConsumerPriceIndex'
NATIONAL_ACTIVITY_INDEX = 'NationalActivityIndex'
NATIONAL_FINANCIAL_CONDITIONS_INDEX = 'NationalFinancialConditionsIndex'
NONFARM_PAYROLL = 'TotalNonfarmPayroll'
TREASURY_YIELD_3MO = 'TreasuryYield3Mo'
TREASURY_YIELD_6MO = 'TreasuryYield6Mo'
TREASURY_YIELD_2YR = 'TreasuryYield2Yr'
TREASURY_YIELD_3YR = 'TreasuryYield3Yr'
TREASURY_YIELD_5YR = 'TreasuryYield5Yr'
TREASURY_YIELD_7YR = 'TreasuryYield7Yr'
TREASURY_YIELD_10YR = 'TreasuryYield10Yr'
TREASURY_YIELD_20YR = 'TreasuryYield20Yr'
TREASURY_YIELD_30YR = 'TreasuryYield30Yr'
UNEMPLOYMENT_RATE = 'UnemploymentRate'
UNEMPLOYMENT_RATE_U4 = 'UnemploymentRateU4'
UNEMPLOYMENT_RATE_U5 = 'UnemploymentRateU5'
UNEMPLOYMENT_RATE_U6 = 'UnemploymentRateU6'

CAPITAL_EXPENDITURE = 'CapitalExpenditure'
COST_OF_REVENUE = 'CostOfRevenue'
EBITDA = 'EBITDA'
ENTERPRISE_VALUE = 'EnterpriseValue'
MARKET_CAP = 'MarketCap'
OPERATING_CASH_FLOW = 'OperatingCashFlow'
TOTAL_ASSETS = 'TotalAssets'
TOTAL_EQUITY = 'TotalEquityGrossMinorityInterest'
TOTAL_LIABILITIES = 'TotalLiabilitiesNetMinorityInterest'
TOTAL_REVENUE = 'TotalRevenue'

def load_financial_data_from_csv(csv_file):
    data_read_start_time = time.time()
    data = pd.read_csv(csv_file)
    """ Financial data is formated with the date as the column name and sorted in descending
        order. We need to transpose columns and rows since we want to use dates as index values.
        The order of rows also should be reversed since it is more intuitive to walk the data
        chronologically.
    """
    if 'ttm' in data:
        data.pop('ttm') # Drop ttm column (trailing twelve months)
    data['name'] = data['name'].apply(lambda n: n.strip()) # Metric names can have white spaces
    data = data.fillna(0) # Replace NaN with 0
    data = data.replace(',', '', regex=True) # Numbers in CSV are serialized as strings with commas
    col_translations = {}
    for c in data.columns:
        if c == 'name':
            col_translations[c] = DATE
        else:
            month, date, year = c.strip().split('/')
            col_translations[c] = '%s-%s-%s' % (year, month, date) # Conform to stock data Date format
            data[c] = data[c].apply(lambda s: float(s)) # Convert strings to floats
    data.rename(columns=col_translations, inplace=True)
    data = data.set_index(DATE).T
    data = data.reindex(index=data.index[::-1])
    logging.info('Finished reading %s after %s seconds:\n%s', csv_file, time.time() - data_read_start_time, data)
    return data

def load_highest_granularity_financial_data_from_csv(symbol, input_dir, filename_suffix):
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

def load_fred_data_from_csv(series_name, input_dir, series_name_translation):
    csv_file = input_dir + '/' + series_name + '.csv'
    data_read_start_time = time.time()
    data = pd.read_csv(csv_file)
    # Data for some dates can be a ".". The FRED website renders these dates as missing.
    data.drop(data[data[series_name] == '.'].index, inplace=True)
    col_translations = {'DATE': DATE, series_name: series_name_translation}
    data.rename(columns=col_translations, inplace=True)
    data = data.set_index(DATE)
    logging.info('Finished reading %s after %s seconds:\n%s',
        csv_file, time.time() - data_read_start_time, data)
    return data

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-f', '--financial-data-input-dir', required=True, help='path to input directory for financial data')
    argparser.add_argument('-m', '--macro-data-input-dir', required=True, help='path to input directory for macro-economic data')
    argparser.add_argument('-o', '--output-dir', required=True, help='path to output directoryfile')
    argparser.add_argument('-s', '--symbol', required=True, help='stock symbol')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    financial_data_input_dir = args.financial_data_input_dir.rstrip('/')
    macro_data_input_dir = args.macro_data_input_dir.rstrip('/')
    output_dir = args.output_dir.rstrip('/')
    symbol = args.symbol.upper()

    assert os.path.isdir(financial_data_input_dir), financial_data_input_dir + ' is not a directory'
    assert os.path.isdir(macro_data_input_dir), macro_data_input_dir + ' is not a directory'
    assert os.path.isdir(output_dir), output_dir + ' is not a directory'

    # Load macro-economic data

    # https://fred.stlouisfed.org/series/RSXFS
    advance_all_retail_sales_data = None
    advance_all_retail_sales_data = load_fred_data_from_csv('RSXFS', macro_data_input_dir, ADVANCE_ALL_RETAIL_SALES)

    # https://fred.stlouisfed.org/series/RSBMGESD
    advance_building_materials_sales_data = None
    advance_building_materials_sales_data = load_fred_data_from_csv('RSBMGESD', macro_data_input_dir, ADVANCE_BUILDING_MATERIALS_SALES)

    # https://fred.stlouisfed.org/series/RSCCAS
    advance_clothing_sales_data = None
    advance_clothing_sales_data = load_fred_data_from_csv('RSCCAS', macro_data_input_dir, ADVANCE_CLOTHING_SALES)

    # https://fred.stlouisfed.org/series/RSFSDP
    advance_food_and_drink_sales_data = None
    advance_food_and_drink_sales_data = load_fred_data_from_csv('RSFSDP', macro_data_input_dir, ADVANCE_FOOD_AND_DRINK_SALES)

    # https://fred.stlouisfed.org/series/RSFHFS
    advance_furniture_sales_data = None
    advance_furniture_sales_data = load_fred_data_from_csv('RSFHFS', macro_data_input_dir, ADVANCE_FURNITURE_SALES)

    # https://fred.stlouisfed.org/series/RSGASS
    advance_gas_station_sales_data = None
    advance_gas_station_sales_data = load_fred_data_from_csv('RSGASS', macro_data_input_dir, ADVANCE_GAS_STATION_SALES)

    # https://fred.stlouisfed.org/series/RSGCS
    advance_grocery_sales_data = None
    advance_grocery_sales_data = load_fred_data_from_csv('RSGCS', macro_data_input_dir, ADVANCE_GROCERY_SALES)

    # https://fred.stlouisfed.org/series/RSSGHBMS
    advance_hobby_sales_data = None
    advance_hobby_sales_data = load_fred_data_from_csv('RSSGHBMS', macro_data_input_dir, ADVANCE_HOBBY_SALES)

    # https://fred.stlouisfed.org/series/RSNSR
    advance_nonstore_sales_data = None
    advance_nonstore_sales_data = load_fred_data_from_csv('RSNSR', macro_data_input_dir, ADVANCE_NONSTORE_SALES)

    # https://fred.stlouisfed.org/series/RSMVPD
    advance_vehicle_and_parts_sales_data = None
    advance_vehicle_and_parts_sales_data = load_fred_data_from_csv('RSMVPD', macro_data_input_dir, ADVANCE_VEHICLE_AND_PARTS_SALES)

    # https://fred.stlouisfed.org/series/MICH
    consumer_inflation_expectation_data = None
    consumer_inflation_expectation_data = load_fred_data_from_csv('MICH', macro_data_input_dir, CONSUMER_INFLATION_EXPECTATION)

    # https://fred.stlouisfed.org/series/DGORDER
    durable_goods_orders_data = None
    durable_goods_orders_data = load_fred_data_from_csv('DGORDER', macro_data_input_dir, DURABLE_GOODS_ORDERS)

    # https://fred.stlouisfed.org/series/DFF
    federal_funds_rate_data = None
    federal_funds_rate_data = load_fred_data_from_csv('DFF', macro_data_input_dir, FEDERAL_FUNDS_RATE)

    # https://fred.stlouisfed.org/series/MEDCPIM158SFRBCLE
    median_consumer_price_index_data = None
    median_consumer_price_index_data = load_fred_data_from_csv('MEDCPIM158SFRBCLE', macro_data_input_dir, MEDIAN_CONSUMER_PRICE_INDEX)

    # https://fred.stlouisfed.org/series/CFNAI
    national_activity_data = None
    national_activity_data = load_fred_data_from_csv('CFNAI', macro_data_input_dir, NATIONAL_ACTIVITY_INDEX)

    # https://fred.stlouisfed.org/series/NFCI
    national_financial_conditions_data = None
    national_financial_conditions_data = load_fred_data_from_csv('NFCI', macro_data_input_dir, NATIONAL_FINANCIAL_CONDITIONS_INDEX)

    # TODO: Might be good to have more granular payroll data.

    # https://fred.stlouisfed.org/series/PAYEMS
    nonfarm_payroll_data = None
    nonfarm_payroll_data = load_fred_data_from_csv('PAYEMS', macro_data_input_dir, NONFARM_PAYROLL)

    # https://fred.stlouisfed.org/series/DGS3MO
    treasury_yield_3mo_data = None
    treasury_yield_3mo_data = load_fred_data_from_csv('DGS3MO', macro_data_input_dir, TREASURY_YIELD_3MO)

    # https://fred.stlouisfed.org/series/DGS6MO
    treasury_yield_6mo_data = None
    treasury_yield_6mo_data = load_fred_data_from_csv('DGS6MO', macro_data_input_dir, TREASURY_YIELD_6MO)

    # https://fred.stlouisfed.org/series/DGS2
    treasury_yield_2yr_data = None
    treasury_yield_2yr_data = load_fred_data_from_csv('DGS2', macro_data_input_dir, TREASURY_YIELD_2YR)

    # https://fred.stlouisfed.org/series/DGS3
    treasury_yield_3yr_data = None
    treasury_yield_3yr_data = load_fred_data_from_csv('DGS3', macro_data_input_dir, TREASURY_YIELD_3YR)

    # https://fred.stlouisfed.org/series/DGS5
    treasury_yield_5yr_data = None
    treasury_yield_5yr_data = load_fred_data_from_csv('DGS5', macro_data_input_dir, TREASURY_YIELD_5YR)

    # https://fred.stlouisfed.org/series/DGS7
    treasury_yield_7yr_data = None
    treasury_yield_7yr_data = load_fred_data_from_csv('DGS7', macro_data_input_dir, TREASURY_YIELD_7YR)

    # https://fred.stlouisfed.org/series/DGS10
    treasury_yield_10yr_data = None
    treasury_yield_10yr_data = load_fred_data_from_csv('DGS10', macro_data_input_dir, TREASURY_YIELD_10YR)

    # https://fred.stlouisfed.org/series/DGS20
    treasury_yield_20yr_data = None
    treasury_yield_20yr_data = load_fred_data_from_csv('DGS20', macro_data_input_dir, TREASURY_YIELD_20YR)

    # https://fred.stlouisfed.org/series/DGS30
    treasury_yield_30yr_data = None
    treasury_yield_30yr_data = load_fred_data_from_csv('DGS30', macro_data_input_dir, TREASURY_YIELD_30YR)

    # https://fred.stlouisfed.org/series/UNRATE
    unemployment_rate_data = None
    unemployment_rate_data = load_fred_data_from_csv('UNRATE', macro_data_input_dir, UNEMPLOYMENT_RATE)

    # https://fred.stlouisfed.org/series/U4RATE
    unemployment_rate_u4_data = None
    unemployment_rate_u4_data = load_fred_data_from_csv('U4RATE', macro_data_input_dir, UNEMPLOYMENT_RATE_U4)

    # https://fred.stlouisfed.org/series/U5RATE
    unemployment_rate_u5_data = None
    unemployment_rate_u5_data = load_fred_data_from_csv('U5RATE', macro_data_input_dir, UNEMPLOYMENT_RATE_U5)

    # https://fred.stlouisfed.org/series/U6RATE
    unemployment_rate_u6_data = None
    unemployment_rate_u6_data = load_fred_data_from_csv('U6RATE', macro_data_input_dir, UNEMPLOYMENT_RATE_U6)

    # Load financial data

    """ We currently load the highest granularity data. In the future, we may not want to
        default to that. Not all information is available at the same granularity.
    """

    balance_sheet_data = None
    balance_sheet_data = load_highest_granularity_financial_data_from_csv(symbol, financial_data_input_dir, 'balance-sheet')
    assert balance_sheet_data is not None

    cash_flow_data = None
    cash_flow_data = load_highest_granularity_financial_data_from_csv(symbol, financial_data_input_dir, 'cash-flow')
    assert cash_flow_data is not None

    income_statement_data = None
    income_statement_data = load_highest_granularity_financial_data_from_csv(symbol, financial_data_input_dir, 'financials')
    assert income_statement_data is not None

    valuation_data = None
    valuation_data = load_highest_granularity_financial_data_from_csv(symbol, financial_data_input_dir, 'valuation_measures')
    assert valuation_data is not None

    # Load stock data

    stock_data_csv_file = financial_data_input_dir + '/' + symbol + '.csv'
    assert os.path.exists(stock_data_csv_file), 'could not find ' + stock_data_csv_file
    stock_data = hindsight.load_stock_data_from_csv(stock_data_csv_file)

    stock_dividends_data = None
    stock_dividends_data_csv_file = financial_data_input_dir + '/' + symbol + '_dividends.csv'
    if os.path.exists(stock_dividends_data_csv_file):
        stock_dividends_data = hindsight.load_stock_data_from_csv(stock_dividends_data_csv_file)

    stock_split_data = None
    stock_split_data_csv_file = financial_data_input_dir + '/' + symbol + '_splits.csv'
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
        #hindsight.CLOSE: [],
        #HIGH_P25: [],
        #HIGH_P75: [],
        #HIGHEST_HIGH: [],
        #LOW_P25: [],
        #LOW_P75: [],
        #LOWEST_LOW: [],
        #VOLUME_P25: [],
        #VOLUME_P50: [],
        #VOLUME_P75: [],
    }

    # Mapping of column to source data DataFrame for convenience when looking up data.
    low_granularity_data = {
        #ADVANCE_ALL_RETAIL_SALES: advance_all_retail_sales_data,
        #ADVANCE_BUILDING_MATERIALS_SALES: advance_building_materials_sales_data,
        #ADVANCE_CLOTHING_SALES: advance_clothing_sales_data,
        #ADVANCE_FOOD_AND_DRINK_SALES: advance_food_and_drink_sales_data,
        #ADVANCE_FURNITURE_SALES: advance_furniture_sales_data,
        #ADVANCE_GAS_STATION_SALES: advance_gas_station_sales_data,
        #ADVANCE_GROCERY_SALES: advance_grocery_sales_data,
        #ADVANCE_HOBBY_SALES: advance_hobby_sales_data,
        #ADVANCE_NONSTORE_SALES: advance_nonstore_sales_data,
        #ADVANCE_VEHICLE_AND_PARTS_SALES: advance_vehicle_and_parts_sales_data,
        #DURABLE_GOODS_ORDERS: durable_goods_orders_data,

        CONSUMER_INFLATION_EXPECTATION: consumer_inflation_expectation_data,
        FEDERAL_FUNDS_RATE: federal_funds_rate_data,
        MEDIAN_CONSUMER_PRICE_INDEX: median_consumer_price_index_data,

        NATIONAL_ACTIVITY_INDEX: national_activity_data,
        NATIONAL_FINANCIAL_CONDITIONS_INDEX: national_financial_conditions_data,
        #NONFARM_PAYROLL: nonfarm_payroll_data,

        #TREASURY_YIELD_3MO: treasury_yield_3mo_data,
        #TREASURY_YIELD_6MO: treasury_yield_6mo_data,
        #TREASURY_YIELD_2YR: treasury_yield_2yr_data,
        #TREASURY_YIELD_3YR: treasury_yield_3yr_data,
        TREASURY_YIELD_5YR: treasury_yield_5yr_data,
        #TREASURY_YIELD_7YR: treasury_yield_7yr_data,
        #TREASURY_YIELD_10YR: treasury_yield_10yr_data,
        #TREASURY_YIELD_20YR: treasury_yield_20yr_data,
        #TREASURY_YIELD_30YR: treasury_yield_30yr_data,

        UNEMPLOYMENT_RATE: unemployment_rate_data,
        #UNEMPLOYMENT_RATE_U4: unemployment_rate_u4_data,
        UNEMPLOYMENT_RATE_U5: unemployment_rate_u5_data,
        #UNEMPLOYMENT_RATE_U6: unemployment_rate_u6_data,

        CAPITAL_EXPENDITURE: cash_flow_data,
        #COST_OF_REVENUE: income_statement_data,
        #EBITDA: income_statement_data,
        #ENTERPRISE_VALUE: valuation_data,
        #MARKET_CAP: valuation_data,
        OPERATING_CASH_FLOW: cash_flow_data,
        #TOTAL_ASSETS: balance_sheet_data,
        #TOTAL_EQUITY: balance_sheet_data,
        #TOTAL_LIABILITIES: balance_sheet_data,
        #TOTAL_REVENUE: income_statement_data,
    }

    """ We have mixed inputs with varying starting dates so the earliest common date is an
        important value. From this point in time, all data is available. This will be our
        starting point for compiling training and evaluation data.
    """
    earliest_common_datetime = None
    for col in low_granularity_data.keys():
        earliest_col_date = np.datetime64(low_granularity_data[col].index[0])
        if not earliest_common_datetime or earliest_col_date > earliest_common_datetime:
            earliest_common_datetime = earliest_col_date
    if not earliest_common_datetime:
        earliest_common_datetime = np.datetime64(stock_data.index[0])
    logging.debug('Final earliest_common_datetime: %s', earliest_common_datetime)

    """ We have to deal with inputs at varying intervals. Some data is available at annual
        intervals. Others are available at monthly, quarterly, or daily intervals. We want
        to try to use whatever we have at hand. To do this, we will maintain a mapping of last
        known values and the dates when those values became public. This mapping will be used
        with any data where the granularity is greater than one day. To initialize our map of
        last known values, we will walk from the earliest available date and stop at
        earliest_common_datetime for each of our input data sources.
    """
    last_known = {}
    for col in low_granularity_data.keys():
        earliest_date_datetime = np.datetime64(low_granularity_data[col].index[0])
        for date in low_granularity_data[col].index:
            date_datetime = np.datetime64(date)
            if date_datetime <= earliest_common_datetime:
                earliest_date_datetime = date_datetime
            else:
                break
        earliest_date = np.datetime_as_string(earliest_date_datetime, unit='D'),
        last_known[col] = {
            DATE: earliest_date,
            'Value': float(low_granularity_data[col].loc[earliest_date][col]),
        }
        # Initialize composite columns
        composite_data_dict[col] = []
        #composite_data_dict[col + AGE] = []
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
    composite_data_earliest_datetime = cursor_datetime + simulation_duration_days
    last_cursor_datetime = np.datetime64(stock_data.index[-1]) - simulation_duration_days

    while cursor_datetime <= last_cursor_datetime:
        cursor_date = np.datetime_as_string(cursor_datetime, unit='D')
        cursor_date_is_trading_day = cursor_date in stock_data.index

        for col in composite_data_dict.keys():
            if col in low_granularity_data:
                if cursor_date in low_granularity_data[col].index:
                    """ Update last_known as information changes and document the date that the
                        changes occurred on.
                    """
                    last_known[col][DATE] = cursor_datetime
                    last_known[col]['Value'] = float(low_granularity_data[col].loc[cursor_date][col])
                if cursor_datetime >= composite_data_earliest_datetime and cursor_date_is_trading_day:
                    composite_data_dict[col].append(last_known[col]['Value'])
                    #composite_data_dict[col + AGE].append((cursor_datetime - last_known[col][DATE]) / np.timedelta64(1, 'D'))

        if cursor_datetime >= composite_data_earliest_datetime and cursor_date_is_trading_day:
            composite_data_dict[DATE].append(cursor_date)
            cursor_date_stock_data = stock_data.loc[cursor_date]

            """ To extract aggregated data, we create a reverse cursor for each cursor_datetime
                and walk back simulation_duration_days.
            """
            reverse_cursor_datetime = copy.deepcopy(cursor_datetime)
            last_reverse_cursor_datetime = reverse_cursor_datetime - simulation_duration_days

            observed_highs = []
            observed_lows = []
            observed_volumes = []
            while reverse_cursor_datetime >= last_reverse_cursor_datetime:
                reverse_cursor_date = np.datetime_as_string(reverse_cursor_datetime, unit='D')
                if reverse_cursor_date in stock_data.index:
                    reverse_cursor_date_stock_data = stock_data.loc[reverse_cursor_date]
                    observed_highs.append(reverse_cursor_date_stock_data[hindsight.HIGH])
                    observed_lows.append(reverse_cursor_date_stock_data[hindsight.LOW])
                    observed_volumes.append(reverse_cursor_date_stock_data[VOLUME])

                reverse_cursor_datetime -= np.timedelta64(1, 'D')

            #composite_data_dict[hindsight.CLOSE].append(cursor_date_stock_data[hindsight.CLOSE])
            #composite_data_dict[HIGH_P25].append(np.percentile(observed_highs, 25))
            #composite_data_dict[HIGH_P75].append(np.percentile(observed_highs, 75))
            #composite_data_dict[HIGHEST_HIGH].append(np.amax(observed_highs))
            #composite_data_dict[LOW_P25].append(np.percentile(observed_lows, 25))
            #composite_data_dict[LOW_P75].append(np.percentile(observed_lows, 75))
            #composite_data_dict[LOWEST_LOW].append(np.amin(observed_lows))
            #composite_data_dict[VOLUME_P25].append(np.percentile(observed_volumes, 25))
            #composite_data_dict[VOLUME_P50].append(np.percentile(observed_volumes, 50))
            #composite_data_dict[VOLUME_P75].append(np.percentile(observed_volumes, 75))

            # Run hindsight simulation and use the outcome as label values for training.

            simulation_end_datetime = (cursor_datetime + simulation_duration_days)
            lots, profits, stats = hindsight.run_simulation(stock_data, cursor_date, np.datetime_as_string(simulation_end_datetime, unit='D'))

            should_trade = 1
            if stats['max_lots_observed'] > MAX_LOTS_OBSERVED:
                should_trade = 0
            if stats['num_lots_counters'][5] > MAX_DAYS_WITH_5_OR_MORE_LOTS:
                should_trade = 0
            if stats['num_lots_counters'][10] > MAX_DAYS_WITH_10_OR_MORE_LOTS:
                should_trade = 0
            if stats['num_lots_counters'][15] > MAX_DAYS_WITH_15_OR_MORE_LOTS:
                should_trade = 0
            composite_data_dict[SHOULD_TRADE].append(should_trade)

        cursor_datetime += np.timedelta64(1, 'D')

    composite_data = pd.DataFrame(composite_data_dict)
    composite_data = composite_data.set_index(DATE)
    pd.set_option('display.max_rows', None)

    """ To make the data ready for training, we just need to load the csv file, drop the date
        column, and remove any duplicate entries.
    """
    logging.info('Training ready DataFrame:\n%s', composite_data.head())
    composite_data.to_csv('%s/%s.csv' % (args.output_dir, symbol))

