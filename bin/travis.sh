#!/usr/bin/env bash

set -e -o pipefail

lein modules npm install
lein modules test
lein modules doo node node once
lein modules doo chrome generic once
lein modules doo firefox generic once
