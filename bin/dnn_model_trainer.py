#!/usr/bin/env/python3
import argparse
import logging
import matplotlib.pyplot as plt
import numpy as np
import os
import pandas as pd
import tensorflow as tf

from matplotlib import rcParams
from sklearn.metrics import accuracy_score, confusion_matrix, precision_score, recall_score
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

def concat_dataframes(csv_list, ones_df=None, zeros_df=None):
    for csv in csv_list:
        input_df = pd.read_csv(csv)
        input_df.pop('Date') # Not a training feature
        input_df = input_df.drop_duplicates()
        if ones_df is None:
            ones_df = input_df.loc[input_df['ShouldTrade'] == 1]
        else:
            ones_df = pd.concat([ones_df, input_df.loc[input_df['ShouldTrade'] == 1]])
        if zeros_df is None:
            zeros_df = input_df.loc[input_df['ShouldTrade'] == 0]
        else:
            zeros_df = pd.concat([zeros_df, input_df.loc[input_df['ShouldTrade'] == 0]])
    return ones_df, zeros_df

def init_and_train_model(X_train_scaled, y_train, X_test_scaled, y_test,
                         training_epochs=100, topology=[128, 256, 256],
                         show_plots=False):
    # From https://towardsdatascience.com/how-to-train-a-classification-model-with-tensorflow-in-10-minutes-fd2b7cfba86
    layers = [tf.keras.layers.Dense(n, activation='relu') for n in topology]
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

    # Default
    history = model.fit(X_train_scaled, y_train, epochs=training_epochs)

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
    return model, accuracy, precision, recall

if __name__ == '__main__':
    argparser = argparse.ArgumentParser()
    argparser.add_argument('--debug', action='store_true', default=False, help='Enable debug logs')

    argparser.add_argument('-c', '--inputs-csv', action='append', default=[], help='path to inputs csv file')
    argparser.add_argument('-d', '--inputs-dir', action='append', default=[], help='path to directory containing inputs csv files')
    argparser.add_argument('--show-plots', action='store_true', default=False, help='enable plots')

    args = argparser.parse_args()

    logging.basicConfig(
        format="%(levelname).1s:%(message)s",
        level=(logging.DEBUG if args.debug else logging.INFO))

    ones_df, zeros_df = concat_dataframes(args.inputs_csv)
    for dir_name in args.inputs_dir:
        for file_name in os.listdir(dir_name):
            if file_name.endswith('.csv'):
                ones_df, zeros_df = concat_dataframes([dir_name + '/' + file_name], ones_df, zeros_df)
    logging.debug('initial ones_df:\n%s\ninitial zeros_df:\n%s', ones_df, zeros_df)

    ones_df_size = len(ones_df)
    zeros_df_size = len(zeros_df)
    if (ones_df_size / zeros_df_size) > 1.05:
        ones_df = ones_df.sample(n=zeros_df_size, random_state=42)
    elif zeros_df_size / ones_df_size > 1.05:
        zeros_df = zeros_df.sample(n=ones_df_size, random_state=42)
    logging.info('equalized ones_df:\n%s\nequalized zeros_df:\n%s', ones_df, zeros_df)

    merged_df = pd.concat([ones_df, zeros_df])
    x = merged_df.drop('ShouldTrade', axis=1) # Features
    y = merged_df['ShouldTrade'] # Labels
    logging.debug('x:\n%s\ny:\n%s', x, y)

    X_train, X_test, y_train, y_test = train_test_split(x, y, test_size=0.2, random_state=42)
    logging.debug('X_train:\n%s\nX_test:\n%s\ny_train:\n%s\ny_test:\n%s', X_train, X_test, y_train, y_test)

    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    logging.debug('X_train_scaled:\n%s\nX_test_scaled:\n%s', X_train_scaled, X_test_scaled)

    
    tf.random.set_seed(42)

    #model, accuracy, precision, recall = init_and_train_model(X_train_scaled, y_train, X_test_scaled, y_test,
    #                                                          training_epochs=100, topology=[128, 256, 256],
    #                                                          show_plots=args.show_plots)
    #model, accuracy, precision, recall = init_and_train_model(X_train_scaled, y_train, X_test_scaled, y_test,
    #                                                          training_epochs=181, topology=[22, 44, 44],
    #                                                          show_plots=args.show_plots)

    # Based on https://machinelearningmastery.com/how-to-configure-the-number-of-layers-and-nodes-in-a-neural-network/
    input_layer_neurons = len(x.columns)
    max_hidden_layers = 3
    max_neurons_per_layer = 256
    training_epochs = 100

    def explore_hyperparameters(input_topology, max_neurons_per_layer, max_depth, good_results=[]):
        for neurons in range(1, max_neurons_per_layer + 1):
            new_topology = input_topology + [neurons]
            model, accuracy, precision, recall = init_and_train_model(
                X_train_scaled, y_train, X_test_scaled, y_test, training_epochs=training_epochs, topology=new_topology)
            result = {
                'accuracy': accuracy,
                'precision': precision,
                'recall': recall,
                'topology': new_topology,
                'training_epochs': training_epochs,
            }
            if accuracy > 0.9:
                good_results.append(result)
            logging.info('result: %s', result)
            logging.info('good_results: %s', good_results)
            if len(new_topology) < max_depth:
                explore_hyperparameters(new_topology, max_neurons_per_layer, max_depth, good_results=good_results)
    explore_hyperparameters([input_layer_neurons], max_neurons_per_layer, max_hidden_layers + 1)

