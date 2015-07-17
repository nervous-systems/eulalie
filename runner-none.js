var path = require("path"), fs = require("fs")

var outputTo    = process.argv[2]
var googBasedir = path.join(process.cwd(), outputTo, 'goog')
if (!fs.existsSync(googBasedir)) {
    console.log("Error: goog directory doesn't exist: "  + googBasedir)
    process.exit(1)
}

var cljsOutputFile =  process.argv[3]
if (!fs.existsSync(cljsOutputFile)) {
    console.log('Bad file: ' + cljsOutputFile)
    process.exit(1)
}

var haveCljsTest = function () {
    return (typeof cemerick !== "undefined" &&
        typeof cemerick.cljs !== "undefined" &&
        typeof cemerick.cljs.test !== "undefined" &&
        typeof cemerick.cljs.test.run_all_tests === "function");
};

var failIfCljsTestUndefined = function () {
    if (!haveCljsTest()) {
        var messageLines = [
            "",
            "ERROR: cemerick.cljs.test was not required.",
            "",
            "You can resolve this issue by ensuring [cemerick.cljs.test] appears",
            "in the :require clause of your test suite namespaces.",
            "Also make sure that your build has actually included any test files.",
            ""
        ];
        console.error(messageLines.join("\n"));
        process.exit(1);
    }
}

require(path.join(googBasedir, 'bootstrap', 'nodejs.js'))

goog.nodeGlobalRequire(cljsOutputFile)

// From closure-compiler's DepsFileParser.java
var depPattern = new RegExp("\\s*goog.addDependency\\((.*)\\);?\\s*")
var argPattern = new RegExp("\\s*([^,]*), (\\[[^\\]]*\\]), (\\[[^\\]]*\\])(?:, (true|false))?\\s*")

var lines = fs.readFileSync(cljsOutputFile, "utf8").split('\n')
var depMatch, argMatch

for(var i = 0; i < lines.length; i++) {
    if((depMatch = depPattern.exec(lines[i])) &&
       (argMatch = argPattern.exec(depMatch[1]))) {
        var provides = eval(argMatch[2])
        for(var j = 0; j < provides.length; j++)
            goog.require(provides[j])
    }
}

failIfCljsTestUndefined();

cemerick.cljs.test.set_print_fn_BANG_(function(x) {
    var x = x.replace(/\n$/, "");
    if (x.length > 0) console.log(x);
});

var success = (function() {
    var results = cemerick.cljs.test.run_all_tests();
    cemerick.cljs.test.on_testing_complete(results, function () {
        process.exit(cemerick.cljs.test.successful_QMARK_(results) ? 0 : 1);
    });
})();
