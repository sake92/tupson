# Tupson JMH benchmarks

This standalone JVM project benchmarks the current Tupson sources.
Run from `benchmarks-jmh`:

```sh
sbt -batch "Jmh/run -f 2 -wi 5 -i 5 -w 1s -r 1s -jvmArgsAppend -Xmx512m -prof gc Tupson(Feature|Io)Benchmark"
```

`TupsonFeatureBenchmark` covers typed products, defaults, collections, escaped
strings, tagged sums, unions, named tuples, and dynamic JSON. Each operation
processes one complete payload. Use `-p scenario=nested,union` with
`TupsonFeatureBenchmark` for a narrower run.

`TupsonIoBenchmark` measures stream output to a counting sink and decoding a
document with several validation errors. Compare average time in µs/op and
`gc.alloc.rate.norm` in B/op.

In the pre-cutover JDK 21 comparison, the new backend took a median of about
one-third the time and allocated 82% fewer bytes per operation across 22 paired
workloads. That was a one-fork run with short iterations. Dynamic JSON decoding
was slower and allocated more, and escaped-string decoding also allocated more.
These results describe those workloads and settings.
