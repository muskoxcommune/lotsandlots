#!/usr/bin/env/python3
import argparse
import os
import requests

QUERY_URL = 'https://www.alphavantage.co/query?'

argparser = argparse.ArgumentParser()
argparser.add_argument('-k', '--apikey', required=True, help='alphavantage.co API key')
argparser.add_argument('-m', '--maturity', choices=['3month', '2year', '5year', '7year', '10year'], required=True, help='maturity')
argparser.add_argument('-o', '--output', required=True, help='output directory')
args = argparser.parse_args()

if not os.path.isdir(args.output):
    print('ERROR: ' + args.output + ' is not a directory')
    exit(1)

r = requests.get(QUERY_URL + 'function=TREASURY_YIELD&interval=daily&maturity=' + args.maturity + '&datatype=json&apikey=' + args.apikey)
with open(args.output + '/' + args.maturity.lower() + '.json', 'w') as fd:
    fd.write(r.text)
