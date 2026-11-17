# Tupson JMH benchmarks

This standalone JVM project measures the JSON writing pipeline against the
current `tupson/src` and `tupson/src-jvm` sources. It is not published.

The benchmark separates building a Jawn `JValue`, rendering a prebuilt value,
rendering the public `toJson` representation, and converting that result to
UTF-8 bytes for payloads containing 5, 25, or 50 items.

Run a short local pass:

```sh
cd benchmarks-jmh
sbt -batch "Jmh/run -f 1 -wi 3 -i 3 -w 1s -r 1s -prof gc JsonWriteBenchmark"
```

The `gc` profiler reports allocated bytes per operation. Use the same JDK and
hardware when comparing changes. The annotations default to two forks, five
warmup iterations, and five measured iterations when the command-line overrides
are omitted.

To isolate renderer instructions with Linux perf:

```sh
sbt -batch "Jmh/run -f 1 -wi 3 -i 5 -w 1s -r 1s -p itemCount=50 -prof perfasm JsonWriteBenchmark.renderPrebuiltAst"
```
