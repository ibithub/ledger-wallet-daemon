#!/usr/bin/env bash

libcore_path=resources/djinni_native_libs/libledger-core.so

jar xf lib/ledger-lib-core.jar $libcore_path
[ $? -ne 0 ] && exit 1

ldd $libcore_path | sed -E 's/ *([^ ]*) .*$/\1/g' | while read lib ; do
  apt-file -l $arch search $lib | while read package ; do echo "$lib : $package" ; done
done | sort | uniq 

