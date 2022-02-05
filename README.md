# lotsandlots
***lotsandlots*** is a technique for realizing regular profits and distributing risk while trading stocks.

#### This project
* Automates the execution of this technique, given an E*Trade account
* Visualizes portfolio data in a way that shows how the technique is being executed

E*Trade is used because they have a trading-enabled API and trades are free. In the future, other platforms could be supported.

## Requirements for running this program
- E*Trade account
- Maven
- OpenJDK

If on Mac OS, you can get Maven and OpenJDK from https://brew.sh/.

## How lotsandlots works

Let's say you believe the stock ABCD of some company will keep or increase it's value for some time. You buy a single lot worth $1000. If the price goes up 3%, you immediately sell this lot for $1030 and realize $30 of profit. After selling your first lot, buy another $1000 lot. As long as the value of the stock continues going up, keep doing this. Collect $30 each time. If the price, instead, goes down 3% after you buy your first lot, buy another $1000 lot. Again, keep doing that as long as the value keeps going down. If you're right about the general direction of the value of ABCD stock and you have the funds, then you can confidently ride dips. During periods of high volatility, the value of a stock may fluxuate 3% up and down regularly - sometimes in the same trading session. If there is a lot of movement up and down, you can use the same few thousand dollars to collect $30 regularly. Repeat this pattern over many stocks. Diversify across different companies and industries. Choose stocks with different behavior characteristics. The goal of the ***lotsandlots*** technique is to ride the day-to-day ups and downs of the market. 

## How to use this app
### Step 1:
Create a config file in your $HOME directory named `lotsandlots.conf`. This project uses https://github.com/lightbend/config. You can look through the documentation there to reference supported syntax. For now, the contents of this file should look like this.
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
In a terminal, clone this project and change into the project root directory, then execute `make run`.
```
$ git@github.com:muskoxcommune/lotsandlots.git
$ cd lotsandlots
$ make run
```
### Step 3:
After the app initializes, a browser should open and redirect you to ETrade's authorization page.

<img width="781" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136672315-366d89b6-f53f-4fc7-934d-26b2de5d037e.png">

Hit accept and copy the verifier code ETrade returns.

<img width="781" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136672345-d5d89ff5-a5c7-4ea4-9ed6-0201d6a06d19.png">

### Step 4:
In a browser, paste this code into the URL bar after `/etrade/authorize?verifier=` as shown below and hit enter.

<img width="493" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136672616-50f7b9a6-a531-47b9-8076-ea0f5546cc19.png">

This completes ETrade's authorization flow. You need to do this every time the app is restarted and also when access tokens expire at midnight US Eastern time as per [ETrade's documentation](https://apisb.etrade.com/docs/api/authorization/get_access_token.html).

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
Restart the app to reload the updated config. This completes app setup.

## API Documentation
This app uses https://swagger.io/ for API documentation. You can load the Swagger UI by going to `http://localhost:5000` in a browser.

<img width="999" alt="Screen Shot" src="https://user-images.githubusercontent.com/5027883/136674448-e08a8f47-1c61-4ffb-88bb-32ae86c0be40.png">
