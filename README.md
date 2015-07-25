# eulalie  [![Build Status](https://travis-ci.org/nervous-systems/eulalie.svg?branch=master)](https://travis-ci.org/nervous-systems/eulalie)

[![Clojars Project](http://clojars.org/io.nervous/eulalie/latest-version.svg)](http://clojars.org/io.nervous/eulalie)

Eulalie is an asynchronous AWS client supporting Clojure/JVM &
Clojurescript/Node, primarily intended as a platform for building higher-level
client libraries.  With the exception of a couple of utility namespaces, Eulalie
exposes a single entrypoint, and uses service-specific transformations to turn
maps describing requests into maps describing responses.

Rather than attempt a gaudy looking Clojure API for each service (e.g. type
mapping, post-processing multimethods, etc.), Eulalie worries about less savoury
details: structural conversions to/from the underlying format, request signing,
retry/backoff policies, etc.

## Services

 -  ![checkmark](https://nervous.io/images/green-tick.png) Dynamo
 -  ![checkmark](https://nervous.io/images/green-tick.png) Dynamo Streams
 -  ![checkmark](https://nervous.io/images/green-tick.png) SNS
 -  ![checkmark](https://nervous.io/images/green-tick.png) SQS
 -  ![checkmark](https://nervous.io/images/yellow-tick.png) Lambda ([documentation](https://github.com/nervous-systems/eulalie/wiki/eulalie.lambda.util))

## Utilities
 - [eulalie.creds](https://github.com/nervous-systems/eulalie/wiki/eulalie.creds) - Retrieval/refreshing of instance-specific IAM role credentials, etc.
 - [eulalie.instance-data](https://github.com/nervous-systems/eulalie/blob/master/src/eulalie/instance_data.cljc) - Utilities for structured retrieval of EC2 instance metadata.

## Higher-level Clients


There are a couple of higher-level clients built with Eulalie, both of which also
support Clojurescript/Node - if you're interested in Dynamo, Dynamo
Streams, SQS, or SNS, it's highly recommended that you rather use either:

 - [Hildebrand](https://github.com/nervous-systems/hildebrand) - Dynamo/Streams
 - [Fink-Nottle](https://github.com/nervous-systems/fink-nottle) - SNS & SNS

# Clojurescript

The motivating use-case for Clojurescript support was the ability to [write AWS Lambda
functions](https://nervous.io/clojure/clojurescript/aws/lambda/node/lein/2015/07/05/lambda/).  That, coupled with environmental restrictions in-browser is why [Node](https://nodejs.org/) is targeted specifically - though it wouldn't be hard to port it to another Clojurescript target.

## `:optimizations` `:advanced`

In order to enable dead-code elimination, eulalie is happy to run under `:optimizations` `:advanced`.  It uses [cljs-nodejs-externs](https://github.com/nervous-systems/cljs-nodejs-externs) to extern the Node standard library.  Its NPM dependencies are declared via [lein-npm](https://github.com/RyanMcG/lein-npm):

 - source-map-support
 - [buffer-crc32](https://www.npmjs.com/package/buffer-crc32)
 - [xml2js](https://www.npmjs.com/package/xml2js) (to ape `clojure.xml` in the SNS/SQS client code)

## And, optimization more generally

The implementation works great, though it's likely there are some adjustments particular to its runtime that someone more Node-experienced might make (e.g. tuning `Agent` construction in [platform.cljs](https://github.com/nervous-systems/eulalie/blob/master/src/eulalie/platform.cljs#L24) for large numbers of concurrent SSL connections to a particular host).  

# API

## Example

```clojure
(ns ...
  (:require [eulalie.core :as eulalie]
            [eulalie.dynamo]
            ...))
            
(eulalie/issue-request!
  {:creds {:access-key ... :secret-key ... [:token :region :endpoint]}
   :service :dynamo
   :target :describe-table
   :body {:table-name ...}})
;; => Channel
;; {:response {:body {:table-name ...} ...}
;;  :retries 0
;;  :request ...}
```

The API for consuming services basically consists of `eulalie/issue-request!`.  Service-specific functionality is incorporated by requiring the appropriate namespace (e.g. `eulalie.dynamo`, above).  There are whole bunch of utilities to make service definition [pretty simple](https://github.com/nervous-systems/eulalie/blob/master/src/eulalie/dynamo.cljc), if that's something you're interested in.

# License

eulalie is free and unencumbered public domain software. For more
information, see http://unlicense.org/ or the accompanying UNLICENSE
file.

