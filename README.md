# lotsandlots

![Status](https://github.com/muskoxcommune/lotsandlots/actions/workflows/tests_on_push.yml/badge.svg) ![Coverage](.github/badges/jacoco.svg) ![Branches](.github/badges/branches.svg)

This project implements a technique for realizing profits regularly and distributing risk while trading stocks.
* It automates the execution of this technique, given an E*Trade account
* It visualizes portfolio data in a way that shows how the technique is being executed

E*Trade is used because they have a trading-enabled API and trades are free. Other platforms could be supported.

## Requirements for running this program
- E*Trade account
- Maven
- OpenJDK

If on Mac OS, you can get Maven and OpenJDK from https://brew.sh/.

## How this program works

Let's say you believe the stock ABCD of some company will keep or increase its value for some time. Buy a single lot worth $1000. If the price goes up 3%, immediately sell your lot for $1030 and realize $30 of profit. After selling this lot, buy another $1000 lot. As long as the value of the stock continues going up, keep doing this. Collect $30 each time. If the price, instead, goes down 3% after you buy your first lot, buy another $1000 lot. Again, keep doing that as long as the value keeps going down. When the value bounces back, sell your lots one at a time and collect $30 each time. If you're right about the general direction of the value of ABCD stock and you have the funds, then you can confidently follow dips. During periods of high volatility, the value of a stock may fluxuate 3% up and down regularly, sometimes in the same trading session. If there is a lot of movement up and down, a few thousand dollars can be deployed to return $30 regularly. The goal of this program is to automate the execution of this technique, allowing a single person to manage more stocks and react more quickly.

# Table of Contents
1. [Initial setup](#initial-setup)
2. [Example configuration](#example-configuration)
3. [How to use this program](#how-to-use-this-program)
4. [API documentation](#api-documentation)
5. [Class diagram](class-diagram)

## Initial setup
### Step 1:
Create a config file in your $HOME directory named `lotsandlots.conf`. This program uses https://github.com/lightbend/config. You can look through the documentation there to reference supported syntax. For now, the contents of this file should look like this.
```
include "application"

etrade {
    consumerKey: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    consumerSecret: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```
You can get a key and secret by going to https://developer.etrade.com/home. You will get a prod key and a sandbox key but only the sandbox key will be immediately enabled. The prod key will only be enabled after you go to https://developer.etrade.com/getting-started and fill out some forms. **Don't** share your key or secret with anyone!

<img width="1003" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136674121-10af6a80-bc75-4f3b-8961-d611c3046409.png">

### Step 2:
In a terminal, clone this project and change into the project root directory. Then execute `make run`.
```
$ git clone git@github.com:muskoxcommune/lotsandlots.git
$ cd lotsandlots
$ make run
```
This is just a mvn command wrapper.
```
$ make run
mvn clean install jetty:run -Dconfig.file=$HOME/lotsandlots.conf
[INFO] Scanning for projects...
[INFO]
[INFO] ----------------------< org.example:lotsandlots >-----------------------
[INFO] Building lotsandlots 1.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ lotsandlots ---
[INFO]
[INFO] --- maven-enforcer-plugin:3.0.0:enforce (enforce) @ lotsandlots ---
[INFO]
```
Maven will download dependencies. This might take some time. Eventually, after running tests, you should see the following log messages.
```
09:05:55.640 [main] INFO  i.l.web.listener.LifecycleListener - Initialized EtradeRestTemplateFactory
09:05:55.640 [main] INFO  i.l.web.listener.LifecycleListener - Go to http://localhost:5000/etrade/authorize if a browser window doesn't open automatically
09:05:55.828 [main] INFO  i.l.web.listener.LifecycleListener - Servlet context initialized
[INFO] Started o.e.j.m.p.JettyWebAppContext@7431f4b8{lotsandlots,/,file:///path/to/lotsandlots/src/main/webapp/,AVAILABLE}{file:///path/to/lotsandlots/src/main/webapp/}
[INFO] Started ServerConnector@5c891ec1{HTTP/1.1, (http/1.1)}{0.0.0.0:5000}
[INFO] Started @8387ms
[INFO] Started Jetty Server
```

### Step 3:
A browser window should open and redirect you to ETrade's authorization page. If it doesn't, open a browser and go to http://localhost:5000/etrade/authorize.

<img width="781" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136672315-366d89b6-f53f-4fc7-934d-26b2de5d037e.png">

Hit accept and copy the verifier code ETrade returns.

<img width="781" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136672345-d5d89ff5-a5c7-4ea4-9ed6-0201d6a06d19.png">

### Step 4:
In a browser, paste this code into the URL bar after `/etrade/authorize?verifier=` as shown below and hit enter.

<img width="493" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136672616-50f7b9a6-a531-47b9-8076-ea0f5546cc19.png">

This completes ETrade's authorization flow. You need to do this every time the program is restarted and also when access tokens expire at midnight US Eastern time as per [ETrade's documentation](https://apisb.etrade.com/docs/api/authorization/get_access_token.html).

### Step 5:
In a browser, go to `/etrade/accounts` as shown below.

<img width="449" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136673751-dba5a2e5-a99e-4c09-83b9-caa1c285cd2f.png">

Copy the accountIdKey of the account you wish to use and add it to the config file you created. It should now look like this.
```
include "application"

etrade {
    accountIdKey: "xxxxxxxxxxxxxxxxxxxxxx"
    consumerKey: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    consumerSecret: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```
Restart the program to reload the updated config. This completes initial setup.

## Example configuration
[^ Table of Contents](#table-of-contents)

```
include "application"

etrade {
    accountIdKey: "xxxxxxxxxxxxxxxxxxxxxx"
    consumerKey: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    consumerSecret: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    defaultOrderCreationThreshold: 0.03
    disableSellOrderCreation: [
    ]
    enableBuyOrderCreation: [
        ABCD, EFGH
    ]
    haltBuyOrderCashBalance: 0
    idealLotSize: 1000
    maxBuyOrdersPerSymbolPerDay: 3
    minLotSize: 900
}
mail {
    enableNotifications: true
    notificationSender: "operator@example.com"
    notificationAddresses: [
        "operator@example.me"
    ]
    sesConfigurationSet: ConfigSet
    smtpHost: email-smtp.us-west-2.amazonaws.com
    smtpPassword: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
    smtpPort: 587
    smtpUser: xxxxxxxxxxxxxxxxxxxxxx
    useTls: true
}
```

## How to use this program
[^ Table of Contents](#table-of-contents)

This program helps you with the tedious administrative parts of trading every day. It does not choose stocks for you. You are responsible for selecting a winning mix of stocks. You will not make money if you choose mostly stocks that depreciate in value. It makes sense to diversify across different companies and industries and hold stocks with different behavior characteristics. The general idea is to ride the day-to-day ups and downs of the market by locking in lots and lots of little gains every day. Curate your mix so that you can harvest gains regardless of which groups of stocks are in rotation.

### Inspecting lots
This program provides a uniquely organized view of lots in a portfolio. Many common tools provide position-centric views where data is only sortable by aggregate values like average cost basis, which obscures the performance of the individual lots that make up positions. This program treats lots as a first-class entity. By default, the page below will visualize only the lowest priced lot in each position but you can have it show all lots by appending `showAllLots=true` as a URL parameter.

<img width="1174" alt="Screen Shot 2022-02-06 at 2 37 53 PM" src="https://user-images.githubusercontent.com/5027883/152704509-d9c5a7fb-6be6-4f0a-a837-6743a3bc2a36.png">

### % unrealized
This value shows how much a lot is up or down compared to the price you paid.
* Rows with values greater than 0 are green
* Rows with values greater than 3% are dark green
* Rows with values between 0% and -3% are pink
* Rows with values less than -3% are dark pink

You should never see any dark green or dark pink in the default view if you are using this program to actively trade every position in your portfolio. Those color are only used when lot values have crossed the typical threshold for action. In the screen shot above, the dark pink lots show positions that are not being actively traded.

### orderStatus
This value will be OK, MISSING, or MISMATCH.
* OK means the number of sell orders matches the number of lots for a position
* MISSING means no sell orders were found for a position
* MISMATCH means there were more or less order than lots for a position

You should see OK most of the time but it's normal to see MISSING or MISMATCH on rows from time to time. If you just acquired a lot, there will be a small delay before a sell order is created for it. During that period, you will see MISMATCH on any lot in that position. The program reacts to mismatches by canceling all existing sell order for a position and creating new sell orders for every lot. Right when it cancels all existing order, you would see MISSING on any lot in position. You should not see MISSING or MISMATCH on any lot for long periods of time. Again, the only exception is positions that are not being actively traded.

### % and $ exposed
These values show relative exposure compared to portfolio value and absolute exposure in dollar value. This information can be used to determine if you want to keep trading a position. If you have limited buying power, you may not wish to keep buying lots on a position that seems to be in sustained decline.

## API Documentation
[^ Table of Contents](#table-of-contents)

This program provides several API endpoints that proxies calls to E*Trade's API. These endpoints exist as a convenience for inspecting data. You can reference API documentation by going to `http://localhost:5000` in a browser.

## Class diagram
[^ Table of Contents](#table-of-contents)

![class diagram](https://user-images.githubusercontent.com/5027883/152702136-2bee3a12-5f98-489f-b36a-6a1aafb4d620.jpg)
