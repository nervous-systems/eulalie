#!/usr/bin/env bash

set -e -o pipefail

aws s3 sync s3://eulalie-build/ . --no-sign-request
mkdir -p ~/.lein/self-installs
mv lein*.jar ~/.lein/self-installs
chmod +x lein

lein modules install
lein modules deps
lein modules test
lein modules doo node node once
lein modules doo chrome generic once
lein modules doo firefox generic once
