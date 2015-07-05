# eulalie  [![Build Status](https://travis-ci.org/nervous-systems/eulalie.svg?branch=master)](https://travis-ci.org/nervous-systems/eulalie)

[![Clojars Project](http://clojars.org/io.nervous/eulalie/latest-version.svg)](http://clojars.org/io.nervous/eulalie)

Asynchronous, pure-Clojure AWS client.  There is currently no
documentation.  Dynamo, SNS and SQS are fully supported.

There is also support for [Lambda](http://aws.amazon.com/documentation/lambda/)
invocations and the retrieval of EC2 instance metadata.

Higher-level clients built with Eulalie:

 - [Hildebrand, a DynamoDB client](https://github.com/nervous-systems/hildebrand)
 - [Fink-Nottle, a client for SQS & SNS](https://github.com/nervous-systems/fink-nottle)

## Usage

```clojure
(eulalie/issue-request!
  :dynamo
  creds
  {:target :describe-table
   :body {:table-name ...}})
;; Where creds is {:access-key, :secret-key} and/or optionally:
;; {:region, :endpoint, :token}

```

### Lambda

```clojure
(eulalie.lambda.util/invoke!
 {:access-key ... :secret-key ...}
 "my-lambda-function"
 :request-response
 {:arg1 "value" :arg2 [:value]})
```

This'll yield a channel containing either `[:ok value]` or `[:error value]` where `value` is a JSON-deserialized data structure.  Errors at the network or AWS level will be communicated by the placement of an `ExceptionInfo` object on the result channel, as with `issue-request!` - the `:ok`/`:error` variant is specifically for errors signalled by the target Lambda function.

## License

eulalie is free and unencumbered public domain software. For more
information, see http://unlicense.org/ or the accompanying UNLICENSE
file.

