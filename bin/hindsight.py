#!/usr/bin/env/python3
import argparse
import logging
import numpy as np
import os
import pandas as pd
import time

IDEAL_LOT_SIZE = 1000
MIN_LOT_SIZE = 900
ORDER_CREATION_THRESHOLD = 0.03

CLOSE = 'Close'
HIGH = 'High'
LOW = 'Low'
OPEN = 'Open'

def buy_new_lot(date, transaction_price, lots, buy_transactions):
    if transaction_price in buy_transactions:
        """ If the difference between the low and the close price is larger than
            ORDER_CREATION_THRESHOLD, we can get stuck in an order creation loop
            where we keep buying and selling lots at the same price. As soon as
            we detect this is happening, we should stop and move on. The likelihood
            of actually buying and selling multiple lots at the same price in a day
            is small. Treating any occurrence as a signal to stop should have minimal
            impact on overall outcomes.
        """
        continue_processing = False
        logging.debug('%s transaction_price %s in buy_transactions, moving on to next date',
            date, transaction_price)
    else:
        continue_processing = True
        buy_transactions[transaction_price] = 1
        new_shares = quantity_from_price(transaction_price)
        lots.append((transaction_price, new_shares, True))
        logging.debug('%s action: buy, price: %s, shares: %s', date, transaction_price, new_shares)
        logging.debug('%s lots: %s', date, lots)
    return lots, buy_transactions, continue_processing

def buy_threshold_from_price(price):
    return (1 - ORDER_CREATION_THRESHOLD) * price

def load_stock_data_from_csv(csv_file):
    data_read_start_time = time.time()
    data = pd.read_csv(csv_file)
    data = data.set_index('Date')
    logging.info('Finished reading %s after %s seconds:\n%s',
        csv_file, time.time() - data_read_start_time, data)
    return data

def process_lot_acquired_after_market_opens(
        date, price, shares, after_open, close_price,
        buy_threshold, sell_threshold, lots, profits, buy_transactions):

    """ If acquired after markets open, we should check against the closing price.
        We don't have enough information to make guesses about intra-day movements
        and volatility so we have to rely on the closing price to decide if a lot
        acquired during the same session might have sold before the session ended.
    """

    if sell_threshold < close_price:
        sell_and_replace_lot(date, sell_threshold, price, shares, lots, profits)
        return lots, profits, buy_transactions, True

    lots.append((price, shares, after_open)) # lot was not sold

    continue_processing = False
    if buy_threshold > close_price:
        lots, buy_transactions, continue_processing = buy_new_lot(date, buy_threshold, lots, buy_transactions)

    return lots, profits, buy_transactions, continue_processing

def process_lot_acquired_before_market_opens(
        date, price, shares, after_open, open_price, high_price, low_price,
        buy_threshold, sell_threshold, lots, profits, buy_transactions):

    """ If acquired before the markets open, we assume a sell order exists.
        If trading opened higher than our sell threshold, the sell order should
        execute at the open price.
    """
    if sell_threshold < open_price:
        sell_and_replace_lot(date, open_price, price, shares, lots, profits)
        return lots, profits, buy_transactions, True

    """ If the daily high exceeds the sell threshold, the sell order should
        execute near the the threshold price, so we use that value to set transaction
        price.
    """
    if sell_threshold < high_price:
        sell_and_replace_lot(date, sell_threshold, price, shares, lots, profits)
        return lots, profits, buy_transactions, True

    lots.append((price, shares, after_open)) # lot was not sold

    continue_processing = False
    if buy_threshold > low_price:
        lots, buy_transactions, continue_processing = buy_new_lot(date, buy_threshold, lots, buy_transactions)

    return lots, profits, buy_transactions, False

def quantity_from_price(price):
    """ Same as logic from io/lotsandlots/etrade/EtradeBuyOrderController.java
        See quantityFromLastPrice.
    """
    if price >= IDEAL_LOT_SIZE:
        return 1
    else:
        q = int(IDEAL_LOT_SIZE / price)
        if (q * price) < MIN_LOT_SIZE:
            q += 1
        return q

def run_simulation(timeseries_data, begin_date, end_date):

    earliest_datetime = np.datetime64(timeseries_data.index[0])
    last_datetime = np.datetime64(timeseries_data.index[-1])

    current_datetime = np.datetime64(begin_date)
    simulation_end_datetime = np.datetime64(end_date)
    assert current_datetime < simulation_end_datetime
    assert current_datetime >= earliest_datetime
    assert simulation_end_datetime <= last_datetime

    logging.debug('Running simulation, begin_date: %s, end_date: %s', begin_date, end_date)

    lots = []
    profits = []

    max_lots_observed = 0
    num_lots_counters = {
        5: 0, 10: 0, 15: 0, 25: 0, 40: 0
    }
    num_dates_with_gt_5_lots = 0
    num_dates_with_gt_10_lots = 0
    num_dates_with_gt_20_lots = 0

    while current_datetime <= simulation_end_datetime:
        """ We don't have per-minute granularity data so we can't made decisions as the
            Java program would. We have to aproximate some behaviors based on daily open,
            high, low, and close values.
        """
        current_date = np.datetime_as_string(current_datetime, unit='D')
        if current_date not in timeseries_data.index:
            # The market isn't always open.
            current_datetime += np.timedelta64(1, 'D')
            continue

        data = timeseries_data.loc[current_date]
        open_price  = float(data[OPEN])
        high_price  = float(data[HIGH])
        low_price   = float(data[LOW])
        close_price = float(data[CLOSE])
        logging.debug('%s constraints: open:%s high:%s low:%s close:%s',
            current_date, open_price, high_price, low_price, close_price)

        buy_transactions = {}

        if not lots:
            """ If we don't start the day with lots, for simplicity, buy a lot as the
                market opens.
            """
            new_shares = quantity_from_price(open_price)
            lots.append((
                open_price, # Purchase price
                new_shares, # Purchased quantity
                False       # Purchased after market open
            ))
            logging.debug('%s action: buy, price: %s, shares: %s', current_date, open_price, new_shares)
        else:
            # Mark all existing lots as purchased before the open
            lots = [(price, shares, False) for price, shares, after_open in lots]

        logging.debug('%s begin lots: %s', current_date, lots)

        # Evaluate existing lots
        num_lots_thresholds_breached = {}
        while lots:
            num_lots = len(lots)
            for threshold in num_lots_counters.keys():
                if num_lots >= threshold:
                    num_lots_thresholds_breached[threshold] = True
            if num_lots > max_lots_observed:
                max_lots_observed = num_lots
            # Assumption: the last appended lot is always lowest
            price, shares, after_open = lots.pop()
            buy_threshold = buy_threshold_from_price(price)
            sell_threshold = sell_threshold_from_price(price)
            if not after_open:
                lots, profits, buy_transactions, continue_processing = process_lot_acquired_before_market_opens(
                        current_date, price, shares, after_open, open_price, high_price, low_price,
                        buy_threshold, sell_threshold, lots, profits, buy_transactions)
                if continue_processing:
                    continue
            else:
                lots, profits, buy_transactions, continue_processing = process_lot_acquired_after_market_opens(
                        current_date, price, shares, after_open, close_price,
                        buy_threshold, sell_threshold, lots, profits, buy_transactions)
                if continue_processing:
                    continue
            # If we don't add any lots, we don't want to loop again.
            break
        logging.debug('%s end lots: %s', current_date, lots)
        for threshold in num_lots_counters.keys():
            if threshold in num_lots_thresholds_breached:
                num_lots_counters[threshold] += 1
        current_datetime += np.timedelta64(1, 'D')

    logging.info('%s %s profit:%s remainingLots:%s maxLotsObserved:%s counter:%s',
        begin_date, end_date, sum(profits), len(lots), max_lots_observed, num_lots_counters)
    return lots, profits, {
        "max_lots_observed": max_lots_observed,
        "num_lots_counters": num_lots_counters
    }

def sell_and_replace_lot(date, transaction_price, purchase_price, shares, lots, profits):
    """ The Java program creates buy orders at the market rate. When a lot is
        sold, we need to immediately buy a new lot if the transaction price (i.e.
        the market rate at the time) is less then the buy threshold of the next
        lowest lot.
    """
    profit = (transaction_price - purchase_price) * shares
    profits.append(profit)
    logging.debug('%s action: sell, price: %s, profit: %s', date, transaction_price, profit)
    logging.debug('%s lots: %s', date, lots)
    if not lots or transaction_price < buy_threshold_from_price(lots[-1][0]):
        new_shares = quantity_from_price(transaction_price)
        lots.append((transaction_price, new_shares, True))
        logging.debug('%s action: buy, price: %s, shares: %s', date, transaction_price, new_shares)
        logging.debug('%s lots: %s', date, lots)
    return lots, profits

def sell_threshold_from_price(price):
    return (1 + ORDER_CREATION_THRESHOLD) * price

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-b', '--begin-date', required=True, help='Begin date')
    argparser.add_argument('-e', '--end-date',   required=True, help='End date')
    argparser.add_argument('-s', '--stock-data', required=True, help='Path to stock data csv file')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    assert os.path.exists(args.stock_data)

    stock_data = load_stock_data_from_csv(args.stock_data)
    run_simulation(stock_data, args.begin_date, args.end_date)
