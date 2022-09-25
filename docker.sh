#!/bin/bash

set -o nounset

readonly IMAGE=mini-ruccola-java:1

build() {
  docker build \
    --build-arg USER=$USER \
    --build-arg GROUP=$(id -gn) \
    -t $IMAGE .
}

run() {
  docker run --rm -it \
    -v "$(pwd):/home/${USER}/work" \
    -v "${HOME}/.m2:/home/${USER}/.m2" \
    $IMAGE "$@"
}

cmd="$1"; shift
case $cmd in
  build | b* )
    build "$@"
;; run | r* )
     run "$@"
;; * )
     echo "invalid command (${cmd})" >&2
     ;;
esac
