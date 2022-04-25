#!/usr/bin/env/python3
import argparse
import os
import requests

QUERY_URL = 'https://www.alphavantage.co/query?'

argparser = argparse.ArgumentParser()
argparser.add_argument('-k', '--apikey', required=True, help='alphavantage.co API key')
argparser.add_argument('-o', '--output', required=True, help='output directory')
args = argparser.parse_args()

if not os.path.isdir(args.output):
    print('ERROR: ' + args.output + ' is not a directory')
    exit(1)

r = requests.get(QUERY_URL + 'function=FEDERAL_FUNDS_RATE&interval=daily&datatype=json&apikey=' + args.apikey)
with open(args.output + '/federal_funds_rate.json', 'w') as fd:
    fd.write(r.text)
