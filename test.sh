#!/bin/bash

set -o errexit

cmd="$1"; shift
case $cmd in
  compile | c*)
    ./test_compile.sh "$@"
    ;;
  all | a*)
    ./test_lex.sh
    ./test_compile.sh
    ;;
esac
