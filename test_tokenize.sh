#!/bin/bash

print_project_dir() {
  local real_path="$(readlink --canonicalize "$0")"
  (
    cd "$(dirname "$real_path")"
    pwd
  )
}

export PROJECT_DIR="$(print_project_dir)"
export TEST_DIR="${PROJECT_DIR}/test"
export TEMP_DIR="${PROJECT_DIR}/z_tmp"

ERRS=""

test_nn() {
  local nn="$1"; shift
  nn="${nn}0"

  local temp_tokens_file="${TEMP_DIR}/test.tokens.txt"

  echo "test_${nn}"

  local exp_tokens_file="${TEST_DIR}/tokenize_exp_${nn}.txt"

  cat ${TEST_DIR}/tokenize_${nn}.vg.txt \
    | ./run.sh tokenize \
    > $temp_tokens_file
  if [ $? -ne 0 ]; then
    ERRS="${ERRS},${nn}_tokenize"
    return
  fi

  ruby test/diff.rb $exp_tokens_file $temp_tokens_file
  if [ $? -ne 0 ]; then
    # meld $exp_tokens_file $temp_tokens_file &

    ERRS="${ERRS},${nn}_diff"
    return
  fi
}

# --------------------------------

mkdir -p z_tmp

mvn --quiet -DskipTests=true package 1>&2
export DO_BUILD=0

ns=

if [ $# -eq 1 ]; then
  ns="$1"
else
  ns="$(seq 1 2)"
fi

for n in $ns; do
  test_nn $(printf "%02d" $n)
done

if [ "$ERRS" = "" ]; then
  echo "ok"
else
  echo "----"
  echo "FAILED: ${ERRS}"
  exit 1
fi
