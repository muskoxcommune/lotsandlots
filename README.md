# lotsandlots
## Requirements
- Maven
- OpenJDK

If on Mac OS, you can get it from https://brew.sh/.
## How to use this app
### Step 1:
Create a config file in your $HOME directory named `lotsandlots.conf`. The contents of this file should look like this.
```
include "application"

etrade {
    consumerKey: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    consumerSecret: "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```
You can get a key and secret by going to https://developer.etrade.com/home. You will get a prod key and a sandbox key but only the sandbox key will be immediately enabled. The prod key will only be enabled after you go to https://developer.etrade.com/getting-started and fill out some forms. **Don't** share your key or secret with anyone!

<img width="1003" alt="Screen Shot 2021-10-09 at 2 22 49 PM" src="https://user-images.githubusercontent.com/5027883/136674121-10af6a80-bc75-4f3b-8961-d611c3046409.png">

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

<img width="493" alt="Screen Shot 2021-10-09 at 12 55 14 PM" src="https://user-images.githubusercontent.com/5027883/136672616-50f7b9a6-a531-47b9-8076-ea0f5546cc19.png">

This completes ETrade's authorization flow. You need to do this every time the app is restarted and also when access tokens expire at midnight US Eastern time as per [ETrade's documentation](https://apisb.etrade.com/docs/api/authorization/get_access_token.html).

### Step 5:
In a browser, go to `/etrade/accounts` as shown below.

<img width="449" alt="Screen Shot 2021-10-09 at 2 02 29 PM" src="https://user-images.githubusercontent.com/5027883/136673751-dba5a2e5-a99e-4c09-83b9-caa1c285cd2f.png">

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
