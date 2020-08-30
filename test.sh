#!/bin/bash

set -o errexit

./test_tokenize.sh

./test_step.sh
