#!/usr/bin/env bash
# This script is necessary to run integration tests
host=$1
port=$2
user=$3
pwd=$4
prefix=$5
drop_mode=$6

command=createdb
if [ -n "$drop_mode" ]; then
echo "!!!!!!!!!!!!!!!!!!!!!"
echo "!!DROPPING DBS MODE!!"
echo "!!!!!!!!!!!!!!!!!!!!!"
command=dropdb
fi

echo "==> PostgreSQL server infos: host " $host ", port " $port

# Databases used in IT tests
tests=(transactionsPartialTest currency_pool test_pool test_pool1 op_pool op_pool_mal info_pool exist_pool fresh_addresses_pool list_pool account_pool delete_pool delete_account balance_pool pool_1 pool_2 pool_3 random_pool multi_pool_mal transactionsCreation4Test ledger anotha_pool same_pool this_pool my_pool your_pool wallet_pool duplicate_pool empty_pool multi_pool random POOL_NAME myPool test_wallet getUtxo_pool )

# Create all DBs with names above
for elem in "${tests[@]}"
do
    echo "     * Creating database : $prefix$elem"
    $command -h $host -p $port $prefix$elem -w -U $user
    echo "     |--> $prefix$elem Done"

done




