#!/usr/bin/env/python3
import argparse
import json
import logging
import numpy as np
import os
import pandas as pd
import tensorflow as tf
import time

from sklearn.model_selection import train_test_split

import mlp_model_explorer
from sklearn.preprocessing import StandardScaler

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-f', '--csv-file', default=[], help='path to csv file')
    argparser.add_argument('checkpoint_dir', metavar='CHECKPOINT_DIR', help='checkpoints directory')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    assert os.path.isdir(args.checkpoint_dir)
    assert args.csv_file

    input_df = pd.read_csv(args.csv_file)
    logging.info(input_df.tail(1))
    exit()

    merged_df = pd.concat([ones_df, zeros_df])
    x = merged_df.drop('ShouldTrade', axis=1) # Features
    y = merged_df['ShouldTrade'] # Labels
    logging.debug('x:\n%s\ny:\n%s', x, y)

    X_train, X_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=int(time.time()))
    logging.info('X_train: %s, X_test: %s', X_train.shape, X_test.shape)

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    logging.debug('X_train_scaled:\n%s\nX_test_scaled:\n%s', X_train_scaled, X_test_scaled)
    
    tf.random.set_seed(int(time.time()))

    with open(args.checkpoint_dir + '/meta.json') as fd:
        meta = json.load(fd)
        model = mlp_model_explorer.init_model([meta['architecture']])
        model.load_weights(args.checkpoint_dir + '/checkpoint')

        accuracy, precision, recall = mlp_model_explorer.test_model(model, X_test_scaled, y_test)
        logging.info('accuracy: %s, precision: %s, recall: %s', accuracy, precision, recall)

