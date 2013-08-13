#!/bin/bash

HSFILE=$1
CFILE=$2
HSFILENAME=$(basename "$HSFILE")
HSFILENAME="${HSFILENAME%.*}"
CFILENAME=$(basename "$CFILE")
CFILENAME="${CFILENAME%.*}"

ghc "$HSFILE"
ghc -I/usr/include/glib-2.0/ -I/usr/lib/glib-2.0/include -o "$CFILENAME" "$CFILE" "$HSFILENAME".o -no-hs-main -lbluetooth