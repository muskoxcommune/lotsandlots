#!/usr/bin/env/python3
import argparse
import os
import requests

QUERY_URL = 'https://www.alphavantage.co/query?'

argparser = argparse.ArgumentParser()
argparser.add_argument('-k', '--apikey', required=True, help='alphavantage.co API key')
argparser.add_argument('-s', '--symbol', required=True, help='symbol')
argparser.add_argument('-o', '--output', required=True, help='output directory')
args = argparser.parse_args()

if not os.path.isdir(args.output):
    print('ERROR: ' + args.output + ' is not a directory')
    exit(1)

r = requests.get(QUERY_URL + 'function=INCOME_STATEMENT&symbol=' + args.symbol + '&apikey=' + args.apikey, timeout=5)
if r.status_code != 200:
    exit(1)
with open(args.output + '/' + args.symbol.lower() + '.json', 'w') as fd:
    fd.write(r.text)
