#!/bin/bash

run_jar() {
  local artifact=vm2gol_v2
  local jar_path=target/${artifact}-0.0.1-SNAPSHOT-jar-with-dependencies.jar
  java -Dfile.encoding=UTF-8 -jar $jar_path "$@"
}

compile() {
  local srcfile="$1"; shift

  cat $srcfile \
    | run_jar lex \
    | run_jar parse \
    | run_jar codegen
}

# --------------------------------

if [ "$DO_BUILD" != "0" ]; then
  mvn --quiet -DskipTests=true package 1>&2
fi

cmd="$1"; shift
case $cmd in
  test_json | lex | parse | codegen )
    run_jar $cmd "$@"
;; compile )
     compile "$@"
;; * )
     echo "unknown command (${cmd})" >&2
     exit 1
     ;;
esac
