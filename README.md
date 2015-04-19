# eulalie  [![Build Status](https://travis-ci.org/nervous-systems/eulalie.svg?branch=master)](https://travis-ci.org/nervous-systems/eulalie)

Asynchronous, pure-Clojure AWS client.  There is currently no
documentation.  Dynamo is fully supported, SNS is partially supported.
More services to come. 

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

