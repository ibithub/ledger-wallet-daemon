#!/usr/bin/env bash

libcore_path=resources/djinni_native_libs/libledger-core.so

jar xf lib/ledger-lib-core.jar $libcore_path
[ $? -ne 0 ] && exit 1

ldd $libcore_path | sed -E 's/ *([^ ]*) .*$/\1/g' | while read lib ; do
   echo -n "$lib :"
   apt-file -l search $lib  | grep -v "dbg" | grep -v "cross" | while read package ; do echo -n " $package" ; done
   echo ""
done | sort | uniq 

