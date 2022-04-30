#!/usr/bin/env/python3
import argparse
import json
import logging

IDEAL_LOT_SIZE = 1000
MIN_LOT_SIZE = 900
ORDER_CREATION_THRESHOLD = 0.03

DAILY_CLOSE_KEY = '4. close'
DAILY_HIGH_KEY = '2. high'
DAILY_LOW_KEY = '3. low'
DAILY_OPEN_KEY = '1. open'
LAST_REFRESHED_DATE_KEY = '3. Last Refreshed'
METADATA_KEY = 'Meta Data'
SYMBOL_KEY = '2. Symbol'
TIMESERIES_KEY = 'Time Series (Daily)'

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

def load_data_to_dict(filename):
    with open(filename) as fd:
        return json.load(fd)

def prepare_timeseries_data(input_dict, begin_date, end_date):
    timeseries_data = []
    last_month = False
    # Expected input format
    """ {
            "Meta Data": {
                "1. Information": "Daily Prices (open, high, low, close) and Volumes",
                "2. Symbol": "X",
                "3. Last Refreshed": "2022-04-21",
                "4. Output Size": "Full size",
                "5. Time Zone": "US/Eastern"
            },
            "Time Series (Daily)": {
                "2022-04-21": {
                    "1. open": "37.0000",
                    "2. high": "37.7900",
                    "3. low": "33.7250",
                    "4. close": "34.6700",
                    "5. volume": "18502433"
                },
                ...
            }
        }
    """
    logging.info("Preparing data: symbol: %s, begin_date: %s, end_date: %s",
        input_dict[METADATA_KEY][SYMBOL_KEY], begin_date, end_date)
    for date in sorted(input_dict[TIMESERIES_KEY].keys()):
        if not timeseries_data and not date.startswith(begin_date):
            continue
        if date.startswith(end_date):
            last_month = True
        elif last_month:
            break
        timeseries_data.append((date, input_dict[TIMESERIES_KEY][date]))
    return timeseries_data

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

def run_simulation(timeseries_data):
    lots = []
    profits = []
    logging.info('Evaluating %s dates, start: %s, end: %s',
        len(timeseries_data), timeseries_data[0][0], timeseries_data[-1][0])

    max_lots_observed = 0
    num_dates_with_gt_10_lots = 0
    num_dates_with_gt_20_lots = 0
    for date, data in timeseries_data:
        """ We don't have per-minute granularity data so we can't made decisions as the
            Java program would. We have to aproximate some behaviors based on daily open,
            high, low, and close values.
        """
        logging.debug('%s constraints: %s', date, data)

        open_price  = float(data[DAILY_OPEN_KEY])
        high_price  = float(data[DAILY_HIGH_KEY])
        low_price   = float(data[DAILY_LOW_KEY])
        close_price = float(data[DAILY_CLOSE_KEY])

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
            logging.debug('%s action: buy, price: %s, shares: %s', date, open_price, new_shares)
        else:
            # Mark all existing lots as purchased before the open
            lots = [(price, shares, False) for price, shares, after_open in lots]

        logging.debug('%s begin lots: %s', date, lots)

        # Evaluate existing lots
        num_lots = 0
        while lots:
            num_lots = len(lots)
            if num_lots > max_lots_observed:
                max_lots_observed = num_lots
            # Assumption: the last appended lot is always lowest
            price, shares, after_open = lots.pop()
            buy_threshold = buy_threshold_from_price(price)
            sell_threshold = sell_threshold_from_price(price)
            if not after_open:
                lots, profits, buy_transactions, continue_processing = process_lot_acquired_before_market_opens(
                        date, price, shares, after_open, open_price, high_price, low_price,
                        buy_threshold, sell_threshold, lots, profits, buy_transactions)
                if continue_processing:
                    continue
            else:
                lots, profits, buy_transactions, continue_processing = process_lot_acquired_after_market_opens(
                        date, price, shares, after_open, close_price,
                        buy_threshold, sell_threshold, lots, profits, buy_transactions)
                if continue_processing:
                    continue
            # If we don't add any lots, we don't want to loop again.
            break
        logging.debug('%s end lots: %s', date, lots)
        if num_lots >= 10:
            num_dates_with_gt_10_lots += 1
        if num_lots >= 20:
            num_dates_with_gt_20_lots += 1
    logging.info('Total profit: %s, Remaining lots: %s', sum(profits), len(lots))
    logging.info('Max lots observed: %s, # Dates /w 10+ lots: %s, # Dates /w 20+ lots: %s',
        max_lots_observed, num_dates_with_gt_10_lots, num_dates_with_gt_20_lots)
    return lots, profits, {
        "max_lots_observed": max_lots_observed,
        "num_dates_with_gt_10_lots": num_dates_with_gt_10_lots,
        "num_dates_with_gt_20_lots": num_dates_with_gt_20_lots
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
    argparser.add_argument('-b', '--begin-date', required=True, help='Begin date')
    argparser.add_argument('-e', '--end-date',   required=True, help='End date')
    argparser.add_argument('-s', '--stock-data', required=True, help='Path to stock data file')
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')
    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    loaded_stock_data = load_data_to_dict(args.stock_data)
    timeseries_data = prepare_timeseries_data(loaded_stock_data, args.begin_date, args.end_date)
    run_simulation(timeseries_data)
