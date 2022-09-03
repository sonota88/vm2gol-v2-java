#!/bin/bash

set -o nounset

test_compile() {
  ./test_compile.sh "$@"
}

test_all() {
  ./test_lex.sh "$@"
  ./test_compile.sh "$@"
}

cmd="$1"; shift
case $cmd in
  compile | c*)
    test_compile "$@"
    ;;
  all | a*)
    test_all "$@"
    ;;
esac
