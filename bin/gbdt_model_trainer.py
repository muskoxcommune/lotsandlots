#!/usr/bin/env/python3
import argparse
import logging
import numpy as np
import os
import pandas as pd
import tensorflow as tf


if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-i', '--inputs-csv', required=True, help='path to inputs csv file')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    input_data = pd.read_csv(args.inputs_csv)
    input_data.pop('Date') # Not a training feature

    input_data_size = len(input_data)
    training_data_start = int(input_data_size * 0.6)

    training_data = input_data.iloc[:training_data_start,:]
    evaluation_data = input_data.iloc[training_data_start:,:]

    all_zero_evaluation_data = evaluation_data[evaluation_data['ShouldTrade'] == 0]
    all_one_evaluation_data = evaluation_data[evaluation_data['ShouldTrade'] == 1]

    logging.info("Training data:\n%s", training_data)
    logging.info("Evaluation data:\n%s", all_zero_evaluation_data)
    logging.info("Evaluation data:\n%s", all_one_evaluation_data)

    y_training = training_data.pop('ShouldTrade')
    y_0_evaluation = all_zero_evaluation_data.pop('ShouldTrade')
    y_1_evaluation = all_one_evaluation_data.pop('ShouldTrade')

    feature_columns = []
    for col in training_data.columns:
        feature_columns.append(tf.feature_column.numeric_column(col, dtype=tf.float64))

    NUM_EXAMPLES = len(y_training)

    def make_input_fn(X, y, n_epochs=None, shuffle=True):
        def input_fn():
            dataset = tf.data.Dataset.from_tensor_slices((dict(X), y))
            if shuffle:
                dataset = dataset.shuffle(NUM_EXAMPLES)
            # For training, cycle thru dataset as many times as need (n_epochs=None).
            dataset = dataset.repeat(n_epochs)
            # In memory training doesn't use batching.
            dataset = dataset.batch(NUM_EXAMPLES)
            return dataset
        return input_fn

    training_input_fn = make_input_fn(training_data, y_training)
    all_zero_evaluation_input_fn = make_input_fn(all_zero_evaluation_data, y_0_evaluation, shuffle=False, n_epochs=1)
    all_one_evaluation_input_fn = make_input_fn(all_one_evaluation_data, y_1_evaluation, shuffle=False, n_epochs=1)

    # Since data fits into memory, use entire dataset per layer. It will be faster.
    # Above one batch is defined as the entire dataset.
    n_batches = 1
    boosted_trees_est = tf.estimator.BoostedTreesClassifier(feature_columns, n_batches_per_layer=n_batches)

    # The model will stop training once the specified number of trees is built, not 
    # based on the number of steps.
    boosted_trees_est.train(training_input_fn, max_steps=2000)

    # Eval.
    all_zero_boosted_trees_result = boosted_trees_est.evaluate(all_zero_evaluation_input_fn)
    all_one_boosted_trees_result = boosted_trees_est.evaluate(all_one_evaluation_input_fn)

    linear_est = tf.estimator.LinearClassifier(feature_columns)
    linear_est.train(training_input_fn, max_steps=100)
    all_zero_linear_result = linear_est.evaluate(all_zero_evaluation_input_fn)
    all_one_linear_result = linear_est.evaluate(all_one_evaluation_input_fn)

    logging.info('boosted 0: %s', all_zero_boosted_trees_result)
    logging.info('boosted 1: %s', all_one_boosted_trees_result)
    logging.info('linear 0: %s', all_zero_linear_result)
    logging.info('linear 1: %s', all_one_linear_result)






