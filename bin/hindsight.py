#!/usr/bin/env/python3
import argparse
import json
import logging
import os
import sys

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

argparser = argparse.ArgumentParser()
argparser.add_argument('-b', '--begin-date', required=True, help='Begin date')
argparser.add_argument('-e', '--end-date',   required=True, help='End date')
argparser.add_argument('-i', '--input',      required=True, help='input file')

argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

args = argparser.parse_args()

logging.basicConfig(
    format="%(levelname).1s:%(message)s",
    level=(logging.DEBUG if args.debug else logging.INFO))

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

with open(args.input) as fd:
    lots = []
    profits = []
    samples = []

    input_dict = json.load(fd)
    logging.info("Evaluating symbol: %s, using data downloaded on: %s",
        input_dict[METADATA_KEY][SYMBOL_KEY], input_dict[METADATA_KEY][LAST_REFRESHED_DATE_KEY])

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
    for date in sorted(input_dict[TIMESERIES_KEY].keys()):
        if not samples and date != args.begin_date:
            continue
        samples.append((date, input_dict[TIMESERIES_KEY][date]))
        if date == args.end_date:
            break
    logging.info('Evaluating %s dates', len(samples))

    for date, data in samples:
        """ We don't have per-minute granularity data so we can't made decisions as the
            Java program would. We have to aproximate some behaviors based on daily open,
            high, low, and close values.
        """
        logging.debug('%s constraints: %s', date, data)
        logging.debug('%s begin lots: %s', date, lots)

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

        # Evaluate existing lots
        while lots:

            # Assumption: the last appended lot is always lowest
            price, shares, after_open = lots.pop()
            buy_threshold  = (1 - ORDER_CREATION_THRESHOLD) * price
            sell_threshold = (1 + ORDER_CREATION_THRESHOLD) * price

            if not after_open:
                """ If acquired before the markets open, we assume a sell order exists.
                    If trading opened higher than our sell threshold, the sell order should
                    execute at the open price.
                """
                if sell_threshold < open_price:
                    transaction_price = open_price
                    profit = (transaction_price - price) * shares
                    logging.debug('%s action: sell, price: %s, profit: %s', date,  transaction_price,  profit)
                    profits.append(profit)

                    """ The Java program creates buy orders at the market rate. When a lot is
                        sold, we need to immediately buy a new lot if the transaction price (i.e.
                        the market rate at the time) is less then the buy threshold of the next
                        lowest lot.
                    """
                    if not lots or transaction_price < (1 - ORDER_CREATION_THRESHOLD) * lots[-1][0]:
                        new_shares = quantity_from_price(transaction_price)
                        lots.append((transaction_price, new_shares, True))
                        logging.debug('%s action: buy, price: %s, shares: %s', date, transaction_price, new_shares)

                    logging.debug('%s lots: %s', date, lots)
                    continue

                """ If the daily high exceeds the sell threshold, the sell order should
                    execute near the the threshold price, so we use that value to set transaction
                    price.
                """
                if sell_threshold < high_price:
                    transaction_price = sell_threshold
                    profit = (transaction_price - price) * shares
                    logging.debug('%s action: sell, price: %s, profit: %s', date, transaction_price, profit)
                    profits.append(profit)

                    if not lots or transaction_price < (1 - ORDER_CREATION_THRESHOLD) * lots[-1][0]:
                        new_shares = quantity_from_price(transaction_price)
                        lots.append((transaction_price, new_shares, True))
                        logging.debug('%s action: buy, price: %s, shares: %s', date, transaction_price, new_shares)

                    logging.debug('%s lots: %s', date, lots)
                    continue

                lots.append((price, shares, after_open)) # lot was not sold

                if buy_threshold > low_price:
                    # Buy a new lot at the threshold price
                    transaction_price = buy_threshold
                    if transaction_price in buy_transactions:
                        """ If the difference between the low and the close price is larger than
                            ORDER_CREATION_THRESHOLD, we can get stuck in an order creation loop
                            where we keep buying and selling lots at the same price. As soon as
                            we detect this is happening, we should stop and move on. The likelihood
                            of actually buying and selling multiple lots at the same price in a day
                            is small. Treating any occurrence as a signal to stop should have minimal
                            impact on overall outcomes.
                        """
                        logging.debug('%s transaction_price in buy_transactions, moving on to next date')
                    else:
                        buy_transactions[transaction_price] = True
                        new_shares = quantity_from_price(buy_threshold)
                        lots.append((buy_threshold, new_shares, True))
                        logging.debug('%s action: buy, price: %s, shares: %s', date, buy_threshold, new_shares)
                        logging.debug('%s lots: %s', date, lots)
                        continue

            else:
                """ If acquired after markets open, we should check against the closing price.
                    We don't have enough information to make guesses about intra-day movements
                    and volatility so we have to rely on the closing price to decide if a lot
                    acquired during the same session might have sold before the session ended.
                """
                if sell_threshold < close_price:
                    transaction_price = sell_threshold
                    profit = (transaction_price - price) * shares
                    logging.debug('%s action: sell, price: %s, profit: %s', date, transaction_price, profit)
                    profits.append(profit)

                    if not lots or transaction_price < (1 - ORDER_CREATION_THRESHOLD) * lots[-1][0]:
                        new_shares = quantity_from_price(transaction_price)
                        lots.append((transaction_price, new_shares, True))
                        logging.debug('%s action: buy, price: %s, shares: %s', date, transaction_price, new_shares)

                    logging.debug('%s lots: %s', date, lots)
                    continue

                lots.append((price, shares, after_open)) # lot was not sold

                if buy_threshold > close_price:
                    # Buy a new lot at the threshold price
                    transaction_price = buy_threshold
                    if transaction_price in buy_transactions:
                        logging.debug('%s transaction_price in buy_transactions, moving on to next date')
                    else:
                        buy_transactions[transaction_price] = True
                        new_shares = quantity_from_price(transaction_price)
                        lots.append((transaction_price, new_shares, True))
                        logging.debug('%s action: buy, price: %s, shares: %s', date, transaction_price, new_shares)
                        logging.debug('%s lots: %s', date, lots)
                        continue

            # If we don't add any lots, we don't want to loop again.
            break
        logging.debug('%s end lots: %s', date, lots)
    logging.info('Total profit: %s, profitDetails: %s', sum(profits), str(profits))
    logging.info('Remaining lots: %s', lots)
