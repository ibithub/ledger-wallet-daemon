# ledger-wallet-daemon &middot; 

[![CircleCI](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon.svg?style=shield)](https://circleci.com/gh/LedgerHQ/ledger-wallet-daemon)
[![codecov](https://codecov.io/gh/LedgerHQ/ledger-wallet-daemon/branch/develop/graph/badge.svg)](https://codecov.io/gh/LedgerHQ/ledger-wallet-daemon)
[![Generic badge](https://img.shields.io/badge/Version-2.6.0-blue)](https://shields.io/)


## Table of content :
- [Run Integration Tests with coverage locally](#run-integration-tests-with-coverage-locally)
- [Updating the libcore](#updating-the-libcore)
- [Using Wallet Daemon with PostgreSQL](#using-wallet-daemon-with-postgresql)
- [Run Dockerized Wallet Daemon](#run-dockerized-wallet-daemon)
- [Integrate Wallet Daemon with Ledger Explorers Regtest](#integrate-wallet-daemon-with-ledger-explorers-regtest)


---
## Run Integration Tests with coverage locally
 ```bash
sbt clean coverage it:test coverageReport
```

Once generated, an html report can be found at `target/scala-2.12/scoverage-report/index.html`

---
## Updating the libcore

When a new version of the libcore is available, we need to update our bindings.

| Prerequisite                                   | Remarks                                      |
| ---------------------------------------------: | :------------------------------------------: |
| [docker](https://www.docker.com/get-started)   | Only needed if you plan to build in local    |
| [sbt](https://www.scala-sbt.org/download.html) |                                              |

1. Check out [lib core project](https://github.com/LedgerHQ/lib-ledger-core)
   ```bash
   git clone --recurse-submodules --depth 1 --branch <branch_or_version_tag> https://github.com/LedgerHQ/lib-ledger-core
   ```

2. Copy `build-jar.sh` and `build-jar-linux.sh` to the lib core project folder
   ```bash
   cp $WALLET_DAEMON_FOLDER/build-jar.sh $LIB_CORE_FOLDER
   cp $WALLET_DAEMON_FOLDER/build-jar-linux.sh $LIB_CORE_FOLDER
   ```

3. `cd` to lib core folder
   ```bash
   cd $LIB_CORE_FOLDER
   ```

4. Two possible situations: either you want a fresh build from your local machine, or you want to
   grab the official lib core artifacts.
   1. For building on your machine, run the script `build-jar.sh` with command: `mac`, `linux` or `all`.
      MacOS can build both `mac` and `linux`. Linux can only build `linux`.
      ```bash
      # Build for mac only. You may want to do this when developing,
      # it's much faster than build for both mac and linux
      bash build-jar.sh mac

      # Build for linux. Linux build is using docker.
      bash build-jar.sh linux

      # Build for both mac and linux
      bash build-jar.sh all
      ```
   2. For getting the official release, pass an extra argument `dl` to the command invocation.
      ```bash
      # Get only the mac release artifacts.
      ./build-jar.sh mac dl

      # Get only the linux artifacts.
      ./build-jar.sh linux dl

      # Get both.
      ./build-jar.sh all dl
      ```

5. Then, you find the jar file in `$LIB_CORE_FOLDER/../build-jar/target/scala-<version>/build-jar-<version>.jar`.
   Replace the `ledger-lib-core.jar` in the lib folder with this file.
   ```bash
   mv $LIB_CORE_FOLDER/../build-jar/target/scala-<version>/build-jar-<version>.jar $WALLET_DAEMON_FOLDER/lib/ledger-lib-core.jar
   ```

6. Push the changes to upstream

7. Add a tag on the new commit with the version of the ledger-core-lib, or the commit
   hash if no version was tagged
---
## Using Wallet Daemon with PostgreSQL

### Setup

1. Make sure to have a PostgreSQL server running,

2. For integration tests make sure to create required databases, you could use the `db.sh` for that:

    ```
    ./dbs.sh <host> <port>
    ```
    To drop databases simply run: 
    ```
    ./dbs.sh <host> <port> drop
    ```

3. For tests with users' databases (i.e. `database.db` created), please use, in an analogous way, `client_dbs.sh` script.

### Configuration

1. By default, Wallet Daemon is using Sqlite3 by default, to activate PostgreSQL set: 
    ```
    CORE_DATABASE_ENGINE=postgres
    ``` 
2. Setting the PostgreSQL connection mandatory infos by setting : 
`CORE_PG_HOST`, `CORE_PG_PORT`, `CORE_PG_USER` and `CORE_PG_PWD` 
which are respectively the hostname, the port exposed by postgres the user and the password to use for establishing the connection.
We are supporting SSL connections. 

3. It is possible to set the connection pool size per wallet pool (e.g. by client) thanks to: 
`PG_CONNECTION_POOL_SIZE_PER_WALLET_POOL`, which is equal to `2` by default and also a prefix name for every databases created by the libcore by setting `CORE_PG_DB_NAME_PREFIX` example : `CORE_PG_DB_NAME_PREFIX=WD_`

## Use postgreSQL for storing Wallet daemon database

By default the wallet daemon will store data in a SQLite3 database. Using postgreSQL can be done by setting some environment
variables:

- [Mandatory] Set `WALLET_DAEMON_DATABASE_ENGINE` to `postgres`
- [Mandatory] Set `CORE_PG_HOST`, `CORE_PG_PORT`, `CORE_PG_USER` and `CORE_PG_PWD` which are respectively the hostname, 
  the port exposed by postgres the user and the password to use for establishing the connection.
- [Optional] Set `WALLET_DAEMON_DB_NAME` with the name of the database you want to use (by default `wallet_daemon`)
- [Optional] Alternatively you can use a custom JDBC by setting `WALLET_JDBC_URL`. This will override all previously set
  configuration to use strictly the JDBC URL you provided.
                 

---
## Run Dockerized Wallet Daemon
Please have a look on  [Docker compose configuration file](docker-compose.yml) for more details on configuration. 
This will create a PostgreSql instance with SSL enabled and the latest development image of wallet daemon ready to talk with.

```
docker-compose up 
```
---
## Integrate Wallet Daemon with Ledger Explorers Regtest 

Wallet Daemon can be run fully offline by using paired with [Ledger Explorers Regtest Project](https://github.com/LedgerHQ/ledger-regtest-docker) 
which run locally all you need to create blockchain scenarios on local test node, start [Explorer and Indexer](https://github.com/LedgerHQ/blockchain-explorer)   
instances ready to be queried by the Wallet Daemon.

In order to use Wallet Daemon on top of it, you just need to configure the test coin explorer urls as following :  

### Customize configuration :
Example for `BTC TESTNET` on a localhost Explorer instance exposing `http` over `20000` port :   

 ```
WALLET_BTC_TESTNET_EXPLORER_ENDPOINT="http://localhost"
WALLET_BTC_TESTNET_EXPLORER_PORT=20000
WALLET_BTC_TESTNET_EXPLORER_VERSION=“v3"

FEES_BTC_TESTNET_PATH="/blockchain/v3/btc_testnet/fees”
 ```

### Test Wallet Daemon Integration 

#### Lets create a btc testnet account on top of Wallet Daemon

First, if you are using *Postgres*, create the database corresponding to the pool name you want. Here we consider the pool name **accounta** :

**Create the pool :**

_Route_ `POST /pools`

`{
    "pool_name": "accounta"
}
`

**Create a btc_regtest  wallet based on btc_testnet :** 

_Route_ `POST /pools/accounta/wallets`

```
{
    "wallet_name": "bitcoin_regtest",
    "currency_name": "bitcoin_testnet"
}
```

**Create the account for my extended pub key (tpub as we are on btc_testnet)**

Example with extended public key as : 
_tpubDAenfwNu5GyCJWv8oqRAckdKMSUoZjgVF5p8WvQwHQeXjDhAHmGrPa4a4y2Fn7HF2nfCLefJanHV3ny1UY25MRVogizB2zRUdAo7Tr9XAjm_

_Route_ `POST /pools/accounta/wallets/bitcoin_regtest/accounts/extended`

(Note the cointype is '1' into the derivation path)

```
{
    "account_index": "0",
    "derivations": [
        {
            "path": "44'/1'/0'",
            "owner": "main",
            "extended_key": "tpubDAenfwNu5GyCJWv8oqRAckdKMSUoZjgVF5p8WvQwHQeXjDhAHmGrPa4a4y2Fn7HF2nfCLefJanHV3ny1UY25MRVogizB2zRUdAo7Tr9XAjm"
        }
    ]
}
```

**Synchronize my account (retrieve operations from explorer) :**

Obviously you need to generate operations on derived addresses from extended key attached to your account 

_Route_ `POST /pools/accounta/wallets/bitcoin_regtest/accounts/1/operations/synchronize`

**Retrieve operations of the account :** 

_Route_ `GET /pools/accounta/wallets/bitcoin_regtest/accounts/1/operations?full_op=1`

