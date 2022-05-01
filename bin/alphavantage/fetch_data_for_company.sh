#!/bin/bash
# vim: nu: tabstop=4: softtabstop=4: shiftwidth=4: expandtab

FETCHER_SCRIPTS=(
    balance_sheet
    cashflow
    income
    overview
    stock
)

do_data_fetch() {
    local SCRIPT=$1
    local API_KEY=$2
    local SYMBOL=$3
    local OUTPUT_PATH=$4
    echo "Fetching: $SYMBOL $SCRIPT" 1>&2
    python bin/alphavantage_${SCRIPT}_fetcher.py -k $API_KEY -s $SYMBOL -o $OUTPUT_PATH
}

show_help_text() {
    echo "Usage: cmd [-d] [-h] [-s SYMBOL]" 1>&2
}

API_KEY=
OUTPUT_PATH=
SYMBOL=
while getopts ":a:dho:s:" OPT; do
  case $OPT in
    a)  API_KEY=$OPTARG
      ;;
    d)  set -x
      ;;
    h)  show_help_text
      ;;
    o)  OUTPUT_PATH=$OPTARG
      ;;
    s)  SYMBOL=$OPTARG
      ;;
    \?) echo "Invalid option: $OPTARG" 1>&2
        show_help_text
      ;;
    :)  echo "Invalid Option: -$OPTARG requires an argument" 1>&2
        show_help_text
  esac
done

if [ -n "$API_KEY" ] && [ -n "$OUTPUT_PATH" ] && [ -n "$SYMBOL" ]; then
    for SCRIPT in ${FETCHER_SCRIPTS[*]}; do
        OUTPUT_DATA_FILE=$OUTPUT_PATH/$SCRIPT/$SYMBOL.json
        if [ ! -f $OUTPUT_DATA_FILE ] || (($(expr $(date +%s) - $(date -r $OUTPUT_DATA_FILE +%s))>86400)); then
            FETCH_EXIT=1
            while [[ $FETCH_EXIT -ne 0 ]]; do
                do_data_fetch $SCRIPT $API_KEY $SYMBOL $OUTPUT_PATH/$SCRIPT
                FETCH_EXIT=$?
                if ((FETCH_EXIT)); then
                    echo "Retrying $SYMBOL $SCRIPT data fetch" 1>&2
                fi
            done
        else
            echo "Skipping fetch: $SYMBOL $SCRIPT" 1>&2
        fi
    done
fi
