#!/bin/bash

set -o errexit

cmd="$1"; shift
case $cmd in
  compile | c*)
    ./test_step.sh "$@"
    ;;
  all | a*)
    ./test_tokenize.sh
    ./test_step.sh
    ;;
esac
