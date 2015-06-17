# eulalie  [![Build Status](https://travis-ci.org/nervous-systems/eulalie.svg?branch=master)](https://travis-ci.org/nervous-systems/eulalie)

[![Clojars Project](http://clojars.org/io.nervous/eulalie/latest-version.svg)](http://clojars.org/io.nervous/eulalie)

Asynchronous, pure-Clojure AWS client.  There is currently no
documentation.  Dynamo, SNS and SQS are fully supported.

Higher-level clients built with Eulalie:

 - [Hildebrand, a DynamoDB client](https://github.com/nervous-systems/hildebrand)
 - [Fink-Nottle, a client for SQS & SNS](https://github.com/nervous-systems/fink-nottle)

## Usage

```clojure
(eulalie/issue-request!
  eulalie.dynamo/service
  {:access-key ... :secret-key ...}
  {:target :describe-table
   :body {:table-name ...}})
```

## License

eulalie is free and unencumbered public domain software. For more
information, see http://unlicense.org/ or the accompanying UNLICENSE
file.

