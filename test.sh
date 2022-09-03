#!/bin/bash

set -o nounset

# --------------------------------

setup() {
  mkdir -p ./z_tmp
}

build() {
  printf "building... " >&2
  mvn --quiet -DskipTests=true package 1>&2
  printf "done\n" >&2

  export DO_BUILD=0
}

# --------------------------------

test_lex() {
  ./test_lex.sh "$@"
}

test_compile() {
  ./test_compile.sh "$@"
}

test_all() {
  ./test_lex.sh "$@"
  ./test_compile.sh "$@"
}

main() {
  setup

  build

  local cmd=
  if [ $# -ge 1 ]; then
    cmd="$1"; shift
  else
    cmd="show_tasks"
  fi

  case $cmd in
    lex | l*)      #task: Run lex tests
      test_lex "$@"
  ;; compile | c*) #task: Run compile tests
      test_compile "$@"
  ;; all | a*)    #task: Run all tests
      test_all "$@"
  ;; * )
       echo "Tasks:"
       grep '#task: ' $0 | grep -v grep
  esac
}

main "$@"
