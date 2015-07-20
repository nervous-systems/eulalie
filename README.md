# eulalie  [![Build Status](https://travis-ci.org/nervous-systems/eulalie.svg?branch=master)](https://travis-ci.org/nervous-systems/eulalie)

[![Clojars Project](http://clojars.org/io.nervous/eulalie/latest-version.svg)](http://clojars.org/io.nervous/eulalie)

Asynchronous AWS client supporting Clojure/JVM & Clojurescript/Node.  Dynamo, Dynamo Streams, SNS and SQS are fully implemented, with more services to come.

There is also support for some [Lambda](http://aws.amazon.com/documentation/lambda/)
functionality (`invoke`, `get-function`, etc.) and an API for the retrieval of EC2 instance metadata.

There are a couple of higher-level clients built with Eulalie - if you're interested in consuming Dynamo, Dynamo Streams, SQS, or SNS, it's highly recommended that you rather use either:

 - [Hildebrand, a DynamoDB & Dynamo Streams client](https://github.com/nervous-systems/hildebrand)
 - [Fink-Nottle, a client for SQS & SNS](https://github.com/nervous-systems/fink-nottle)

Some of the other functionality (`eulalie.lambda.util`, `eulalie.creds`, `eulalie.instance-data`) is intended for direct consumption).

## Clojurescript

Because eulalie relies on EDN representations of everything (requests, responses, credentials, etc.), and was pure Clojure, it seemed natural to have it target Clojurescript. The motivating use-case was [writing AWS Lambda
functions in
Clojurescript](https://nervous.io/clojure/clojurescript/aws/lambda/node/lein/2015/07/05/lambda/) - which is why [Node](https://nodejs.org/) is targeted specifically.  The implementation works great, though it's very likely that there are some adjustments particular to its runtime that someone more Node-experienced might make (e.g. tuning `Agent` construction in [platform.cljs](https://github.com/nervous-systems/eulalie/blob/1e2b3222a665691effa6ec2fa2f4a49792822aa8/src/eulalie/platform.cljs#L20) for large numbers of concurrent SSL connections to a particular host).  

### NPM Dependencies

Dependencies declared via [lein-npm](https://github.com/RyanMcG/lein-npm) are `source-map-support`, [crc](https://www.npmjs.com/package/crc) and [xml2js](https://www.npmjs.com/package/xml2js).  `xml2js` is used for SNS/SQS (to ape `clojure.xml`), and will be eliminated if neither `eulalie.sns` nor `eulalie.sqs` are required.  The `dev` profile loads additional Node deps for the tests.

 - If `source-map-support` isn't available at runtime, nothing bad will happen
 - Similarly, if for some reason you'd like to disable response checksum validation, removing `crc` won't actually break anything
 - `xml2js` could probably be eliminated with a small amount of Clojurescript.
 - 
## API

### Example

```clojure
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

The API for consuming services basically consists of `eulalie/issue-request!`.  Service-specific functionality is incorporated by requiring the appropriate namespace (e.g. `eulalie.dynamo`, above).  There are whole bunch of utilities to make service definition [pretty simple](https://github.com/nervous-systems/eulalie/blob/master/src/eulalie/dynamo.cljc).

### Lambda

```clojure
(eulalie.lambda.util/request!
 {:access-key ... :secret-key ...}
 "my-lambda-function"
 {:arg1 "value" :arg2 [:value]})
```

This'll yield a channel containing either `[:ok value]` or `[:error value]` where `value` is a JSON-deserialized data structure.  Errors at the network or AWS level will be communicated by the placement of an `ExceptionInfo` object on the result channel, as with `issue-request!` - the `:ok`/`:error` variant is specifically for errors signalled by the target Lambda function.

## License

eulalie is free and unencumbered public domain software. For more
information, see http://unlicense.org/ or the accompanying UNLICENSE
file.

