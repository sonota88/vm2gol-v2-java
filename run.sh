#!/bin/bash

run_jar() {
  local artifact=vm2gol_v2
  java -jar target/${artifact}-0.0.1-SNAPSHOT-jar-with-dependencies.jar "$@"
}

compile() {
  local srcfile="$1"; shift

  cat $srcfile \
    | run_jar tokenize \
    | run_jar parse \
    | run_jar codegen
}

# --------------------------------

if [ "$DO_BUILD" != "0" ]; then
  mvn -DskipTests=true package 1>&2
fi

cmd="$1"
case $cmd in
  tokenize | parse | codegen)
    run_jar "$@"
    ;;
  *)
    compile "$@"
    ;;
esac
