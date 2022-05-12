#!/usr/bin/env/python3
import argparse
import hashlib
import json
import logging
import matplotlib.pyplot as plt
import numpy as np
import os
import pandas as pd
import tensorflow as tf
import time

from matplotlib import rcParams
from sklearn.metrics import accuracy_score, confusion_matrix, precision_score, recall_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler


def concat_dataframes(csv_list):
    train_df = None
    eval_df = None
    for csv in csv_list:
        input_df = pd.read_csv(csv)
        input_df = input_df.drop_duplicates()
        input_df_size = len(input_df)
        split_idx = int(input_df_size * 0.8)

        # Training DataFrame

        raw_train_df = input_df.iloc[:split_idx,:]
        logging.debug('%s train_df:\n%s', csv, raw_train_df)

        train_ones_df = raw_train_df.loc[raw_train_df['ShouldTrade'] == 1]
        logging.debug('%s train_ones_df:\n%s', csv, train_ones_df)
        ones_df_size = len(train_ones_df)

        train_zeros_df = raw_train_df.loc[raw_train_df['ShouldTrade'] == 0]
        logging.debug('%s train_zeros_df:\n%s', csv, train_zeros_df)
        zeros_df_size = len(train_zeros_df)

        """ We don't want to have unbalanced training data so we resize our DataFrames based on
            the lesser of the two.

            TODO: Instead of trimming the lesser DataFrame, we could also try oversampling by
                  duplicating random 0s.
        """
        if ones_df_size > zeros_df_size:
            train_ones_df = train_ones_df.sample(n=zeros_df_size, random_state=random_seed())
        elif zeros_df_size > ones_df_size:
            train_zeros_df = train_zeros_df.sample(n=ones_df_size, random_state=random_seed())
        new_train_df = pd.concat([train_ones_df, train_zeros_df])
        train_df = new_train_df if train_df is None else pd.concat([train_df, new_train_df])
        train_df.pop('Date') # Date is not a training feature

        # Evaluation DataFrame

        new_eval_df = input_df.iloc[split_idx:,:]
        logging.debug('%s eval_df:\n%s', csv, new_eval_df)
        eval_df = new_eval_df if eval_df is None else pd.concat([eval_df, new_eval_df])
        eval_df.pop('Date')

    return train_df, eval_df

def explore_hyperparameters(X_train_scaled, y_train, X_test_scaled, y_test,
                            input_architecture, min_neurons_per_layer, max_neurons_per_layer, max_depth, training_epochs,
                            checkpoint_dir=None, results=None):
    for neurons in range(min_neurons_per_layer, max_neurons_per_layer + 1)[::-1]:
        new_architecture = input_architecture + [neurons]
        _, accuracy, precision, recall, sha = init_and_train_model(
            X_train_scaled, y_train, X_test_scaled, y_test,
            architecture=new_architecture, checkpoint_dir=checkpoint_dir, training_epochs=training_epochs)
        if results is None:
            results = {'sha': [],
                       'accuracy': [],
                       'precision': [],
                       'recall': [],
                       'architecture': [],
                       'training_epochs': []}
        results['sha'].append(sha),
        results['accuracy'].append(accuracy),
        results['precision'].append(precision),
        results['recall'].append(recall),
        results['architecture'].append('/'.join([str(i) for i in new_architecture])),
        results['training_epochs'].append(training_epochs),
        if len(new_architecture) < max_depth:
            results = explore_hyperparameters(new_architecture,
                                              min_neurons_per_layer, max_neurons_per_layer, max_depth, training_epochs,
                                              checkpoint_dir=checkpoint_dir, results=results)
    return results

def init_and_train_model(X_train_scaled, y_train, X_test_scaled, y_test,
                         architecture=[10], checkpoint_dir=None, training_epochs=100,
                         show_plots=False):

    sha = hashlib.sha1(str(time.time()).encode('utf-8')).hexdigest()
    model = init_model(architecture)

    # Default
    if checkpoint_dir is None:
        history = model.fit(X_train_scaled, y_train, epochs=training_epochs)
    else:
        assert os.path.isdir(checkpoint_dir), checkpoint_dir + ' is not a directory'
        checkpoint_dir = checkpoint_dir + '/' + sha
        os.mkdir(checkpoint_dir, mode=0o755)
        checkpoint_callback = tf.keras.callbacks.ModelCheckpoint(filepath=(checkpoint_dir + '/checkpoint'),
                                                                 save_weights_only=True,
                                                                 verbose=1)
        history = model.fit(X_train_scaled, y_train, epochs=training_epochs, callbacks=[checkpoint_callback])

    if show_plots:
        rcParams['figure.figsize'] = (18, 8)
        rcParams['axes.spines.top'] = False
        rcParams['axes.spines.right'] = False
        plt.plot(
            np.arange(1, training_epochs + 1),
            history.history['loss'], label='Loss'
        )
        plt.plot(
            np.arange(1, training_epochs + 1),
            history.history['accuracy'], label='Accuracy'
        )
        plt.plot(
            np.arange(1, training_epochs + 1),
            history.history['precision'], label='Precision'
        )
        plt.plot(
            np.arange(1, training_epochs + 1),
            history.history['recall'], label='Recall'
        )
        plt.title('Evaluation metrics', size=20)
        plt.xlabel('Epoch', size=14)
        plt.legend();
        plt.show()

    accuracy, precision, recall = test_model(model, X_test_scaled, y_test)

    return model, accuracy, precision, recall, sha

def init_model(architecture):
    layers = [tf.keras.layers.Dense(n, activation='relu') for n in architecture]
    layers.append(tf.keras.layers.Dense(1, activation='sigmoid'))
    model = tf.keras.Sequential(layers)
    model.compile(
        loss=tf.keras.losses.binary_crossentropy,
        optimizer=tf.keras.optimizers.Adam(lr=0.03),
        metrics=[
            tf.keras.metrics.BinaryAccuracy(name='accuracy'),
            tf.keras.metrics.Precision(name='precision'),
            tf.keras.metrics.Recall(name='recall')
        ]
    )
    return model

def random_seed():
    #return int(time.time())
    return 42

def test_model(model, X_test_scaled, y_test):
    predictions = model.predict(X_test_scaled)
    prediction_classes = [1 if prob > 0.5 else 0 for prob in np.ravel(predictions)]
    logging.debug('Predictions: %s', prediction_classes)

    """ Confusion matrixInterpretation:
        [[true_positive false_negative]
         [false_positive true_negative]]
    """
    logging.debug('Confusion Matrix:\n%s', confusion_matrix(y_test, prediction_classes))

    # From https://blog.paperspace.com/deep-learning-metrics-precision-recall-accuracy/
    """ Accuracy is a metric that generally describes how the model performs across all classes.
        It is useful when all classes are of equal importance. It is calculated as the ratio
        between the number of correct predictions to the total number of predictions.

        Precision is calculated as the ratio between the number of Positive samples correctly
        classified to the total number of samples classified as Positive (either correctly or
        incorrectly). The precision measures the model's accuracy in classifying a sample as
        positive.

        The recall is calculated as the ratio between the number of Positive samples correctly
        classified as Positive to the total number of Positive samples. The recall measures the
        model's ability to detect Positive samples. The higher the recall, the more positive
        samples detected.
    """
    accuracy = accuracy_score(y_test, prediction_classes)
    precision = precision_score(y_test, prediction_classes)
    recall = recall_score(y_test, prediction_classes)
    return accuracy, precision, recall

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-L', '--max-hidden-layers', type=int, default=1, help='maximum number of hidden layers')
    argparser.add_argument('-M', '--max-neurons-per-layer', type=int, default=1, help='maximum number of neurons in hidden layers')
    argparser.add_argument('-c', '--checkpoint_dir', help='checkpoints directory')
    argparser.add_argument('-d', '--csv-dir', action='append', default=[], help='path to directory containing csv files')
    argparser.add_argument('-e', '--training-epochs', type=int, default=100, help='number of epochs to train models')
    argparser.add_argument('-f', '--csv-file', action='append', default=[], help='path to csv file')
    argparser.add_argument('-m', '--min-neurons-per-layer', type=int, default=1, help='minimum number of neurons in hidden layers')
    argparser.add_argument('-r', '--repetitions', type=int, default=1, help='number of times to repeat experiment')
    argparser.add_argument('--show-plots', action='store_true', default=False, help='enable plots')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    assert args.csv_dir or args.csv_file
    assert args.max_neurons_per_layer >= args.min_neurons_per_layer

    merged_train_df, merged_eval_df = concat_dataframes(args.csv_file)
    for dir_name in args.csv_dir:
        for file_name in os.listdir(dir_name):
            if file_name.endswith('.csv'):
                new_train_df, new_eval_df = concat_dataframes([dir_name + '/' + file_name])
                merged_train_df = new_train_df if merged_train_df is None else pd.concat([merged_train_df, new_train_df])
                merged_eval_df = new_eval_df if merged_eval_df is None else pd.concat([merged_eval_df, new_eval_df])
    logging.info('merged_train_df: %s', merged_train_df)
    logging.info('0 labels in merged_train_df: %s', len(merged_train_df.loc[merged_train_df['ShouldTrade'] == 0]))
    logging.info('merged_eval_df: %s', merged_eval_df)
    logging.info('0 labels in merged_eval_df: %s', len(merged_eval_df.loc[merged_eval_df['ShouldTrade'] == 0]))

    X_train = merged_train_df.drop('ShouldTrade', axis=1) # Features
    y_train = merged_train_df['ShouldTrade'] # Labels
    X_test = merged_eval_df.drop('ShouldTrade', axis=1)
    y_test = merged_eval_df['ShouldTrade']
    logging.info('X_train: %s, X_test: %s', X_train.shape, X_test.shape)

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    logging.debug('X_train_scaled:\n%s\nX_test_scaled:\n%s', X_train_scaled, X_test_scaled)
    
    tf.random.set_seed(random_seed())

    results_df = None
    for _ in range(args.repetitions):
        new_results = explore_hyperparameters(
            X_train_scaled, y_train, X_test_scaled, y_test,
            [], args.min_neurons_per_layer, args.max_neurons_per_layer, args.max_hidden_layers, args.training_epochs,
            args.checkpoint_dir
        )
        results_df = pd.DataFrame(new_results) if results_df is None else pd.concat([results_df, pd.DataFrame(new_results)])

    results_df = results_df.sort_values('accuracy', ascending=False)
    results_df.reset_index(drop=True, inplace=True)
    logging.info("features: %s", ','.join(sorted(X_train.columns)))
    logging.info("results:\n%s", results_df)
    logging.info("avg.accuracy: %s, avg.precision: %s, avg.recall: %s",
        np.average(results_df['accuracy']), np.average(results_df['precision']), np.average(results_df['recall']))
    top_result = results_df.head(1)
    top_sha = top_result['sha'].iloc[0]
    logging.info("top.sha: %s", top_sha)

    if args.checkpoint_dir:
        with open(args.checkpoint_dir + '/' + top_sha + '/meta.json', 'w') as fd:
            meta = {
                'accuracy': top_result['accuracy'].iloc[0],
                'architecture': top_result['architecture'].iloc[0],
                'features': list(X_train.columns),
                'precision': top_result['precision'].iloc[0],
                'recall': top_result['recall'].iloc[0],
                'sha': top_result['sha'].iloc[0],
                'training_epochs': args.training_epochs
            }
            json.dump(meta, fd, indent=4)
