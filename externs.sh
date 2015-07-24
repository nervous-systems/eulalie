#!/bin/sh

lein npm install && \
    mkdir -pv src/eulalie/externs/node-stdlib && \
    cp node_modules/nodejs-externs/externs/*.js src/eulalie/externs/node-stdlib
