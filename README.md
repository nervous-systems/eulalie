# eulalie  [![Build Status](https://travis-ci.org/nervous-systems/eulalie.svg?branch=master)](https://travis-ci.org/nervous-systems/eulalie)

[![Clojars Project](http://clojars.org/io.nervous/eulalie/latest-version.svg)](http://clojars.org/io.nervous/eulalie)

Asynchronous AWS client supporting Clojure/JVM & Clojurescript/Node.  Dynamo, Dynamo Streams, SNS & SQS are currently fully supported.

There is also support for some [Lambda](http://aws.amazon.com/documentation/lambda/)
functionality (`invoke`, `get-function`, etc.) and an API for the retrieval of EC2 instance metadata.

There are a couple of higher-level clients built with Eulalie - if you're interested in consuming Dynamo, Dynamo Streams, SQS, or SNS, it's highly recommended that you rather use either:

 - [Hildebrand, a DynamoDB & Dynamo Streams client](https://github.com/nervous-systems/hildebrand)
 - [Fink-Nottle, a client for SQS & SNS](https://github.com/nervous-systems/fink-nottle)

Some of the other functionality (`eulalie.lambda.util`, `eulalie.creds`, `eulalie.instance-data` is intended for direct consumption).

## Clojurescript

Because eulalie relies on EDN representation of everything (requests, credentials, regions, etc.), and was pure Clojure, it seemed natural to have it target Clojurescript. The motivating use-case was [writing AWS Lambda
functions in
Clojurescript](https://nervous.io/clojure/clojurescript/aws/lambda/node/lein/2015/07/05/lambda/) - which is why [Node](https://nodejs.org/) is targeted specifically.  The Node implementation works great, though it's very likely that there are some adjustments particular to its runtime that someone more Node-experienced might make (e.g. tuning `Agent` construction in [platform.cljs](https://github.com/nervous-systems/eulalie/commit/1e2b3222a665691effa6ec2fa2f4a49792822aa8#L20) for large numbers of concurrent SSL connections to a particular host).

## Example

```clojure
(eulalie/issue-request!
  :dynamo
  creds
  {:target :describe-table
   :body {:table-name ...}})
```

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

