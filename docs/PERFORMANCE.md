# Performance

This page reports how fast Fast Compose parses and interprets Compose code, how it limits recomposition, and how it
keeps a host app responsive. All numbers come from
[`InterpreterBenchmarkTest`](../fast-compose/src/jvmTest/kotlin/ma7moud3ly/fast/compose/interpreter/InterpreterBenchmarkTest.kt),
run on a GitHub-hosted cloud runner by the [Benchmark workflow](../.github/workflows/benchmark.yml).

## Summary

| Operation | Cost |
|---|---|
| Parsing a 59-line preview file | 0.33 ms (5.5 µs per line) |
| Call to a function binding, e.g. `Text(…)` | 0.32 µs |
| Call to a member binding, e.g. `Modifier.padding(…)` | 0.27 µs |
| Call to an interpreted function, including its body | 0.70 µs |
| Loop iteration with local variables and a condition | 0.35 µs |

Numbers are for version 0.3.0. At these costs, a 16.7 ms frame (60 Hz) fits about 50,000 binding calls. Typical previews make hundreds, so the
interpreter takes a small share of a frame; Compose's own measuring, layout and drawing take longer. Parsing a file
takes less than a millisecond, far below the 150 ms pause after which edits are parsed.

## Method

**Scope.** The benchmark measures parsing and the interpreter running plain code: function calls, loops, local
variables, data classes, collections and calls to bindings. It does not measure composition, layout or drawing,
which are done by Compose itself and cost the same as in a compiled app.

**Harness.** Each workload is a top-level function `test()`, parsed once and called through `Interpreter.call`. It
runs 10 times to warm up the JIT compiler, then 7 times; the median of the 7 is reported. Every run checks that
`test()` returns the expected value, so no work is skipped. The parsing workload parses the same file 20 times per
run and checks that it has no errors; the reported time is per parse. Times come from `kotlin.time.measureTime`,
which uses a monotonic clock.

**Per-operation costs.** The cost per operation is the median time divided by the number of operations in the
workload.

**Binding calls.** The cost of one binding call is isolated with a matched baseline: the same loop without the call.
Both loops run 100,000 iterations. After warming up both, they are timed in alternation, 15 times each, and the
median of the 15 paired differences, divided by 100,000, is the cost of one call. Timing in pairs cancels drift in
JIT compilation and machine speed, which affects both loops of a pair alike.

**Repetition.** The workflow runs the whole benchmark 6 times, each in a new JVM. Results are the median of the 6
runs, with the lowest and highest value.

**Environment.**

| | |
|---|---|
| Machine | GitHub-hosted runner `ubuntu-latest`: 4 vCPUs of an AMD EPYC 7763, 15 GB of memory |
| OS | Linux 7.0 (Azure), x86-64 |
| JVM | OpenJDK 64-Bit Server VM 21.0.11, heap limited to 512 MB |

The benchmark prints the JVM, OS, processor count and heap size; the workflow adds the runner's CPU and memory to its
summary.

## Workloads

| Workload | Code | Operations |
|---|---|---|
| Parsing | A 59-line preview file: a data class, state, layout, Material components, a lazy list and two previews | 59 lines |
| Recursion | `fib(20)` with `if (n < 2) n else fib(n - 1) + fib(n - 2)` | 21,891 calls |
| Loops and locals | `for` over 50,000 values; a local `val`, a multiplication, `%`, a comparison and a conditional `+=` | 50,000 iterations |
| Classes and collections | 5,000 data class instances through `map`, `filter`, `map` and `sum` | 5,000 items |
| Function binding | `repeat(100000) { total += square(1) }` against `repeat(100000) { total += 1 }`; `square` is a binding | 100,000 calls |
| Member binding | `repeat(100000) { p = p.shift(1) }` against `repeat(100000) { p = p }`; `shift` is a member binding | 100,000 calls |

## Results

Median of 6 runs, with the range across runs:

| Workload | Median time | Per operation | Range per operation |
|---|---|---|---|
| Parsing | 0.33 ms per parse | 5.46 µs per line | 5.02 – 6.28 |
| Recursion | 15.34 ms | 0.701 µs per call | 0.670 – 0.722 |
| Loops and locals | 17.51 ms | 0.350 µs per iteration | 0.329 – 0.379 |
| Classes and collections | 8.97 ms | 1.794 µs per item | 1.051 – 2.037 |
| Function binding loop | 53.00 ms | 0.530 µs per iteration | 0.514 – 0.549 |
| Function binding baseline | 21.20 ms | 0.212 µs per iteration | 0.199 – 0.222 |
| Member binding loop | 42.91 ms | 0.430 µs per iteration | 0.408 – 0.436 |
| Member binding baseline | 15.36 ms | 0.154 µs per iteration | 0.135 – 0.166 |

Binding calls, from the paired differences:

| | Median | Range | Per run |
|---|---|---|---|
| Function binding call | 0.320 µs | 0.305 – 0.331 | 0.322, 0.309, 0.331, 0.317, 0.329, 0.305 |
| Member binding call | 0.272 µs | 0.266 – 0.293 | 0.270, 0.275, 0.266, 0.293, 0.267, 0.273 |

A binding call costs about half of an interpreted function call: it looks up the binding, matches the arguments to
its signature and calls compiled Compose code, while an interpreted call also creates a scope and evaluates a body.

Most workloads vary by less than 10% between runs. The collections workload varies more: it took either about 1.1 µs
or about 1.7 – 2.0 µs per item, in two groups, which points to differences in JIT compilation or CPU frequency between
runs rather than to the interpreter.

Compared with 0.2.0, on the same CPU model, loops cost the same, while calls cost more: interpreted function calls
about 35%, binding calls about 20%. Calls now also pick overloads by argument types and check whether a property can
be called. Parsing and collections are faster.

## Recomposition

Each user `@Composable` function that returns nothing runs in its own restart scope, as compiled composables do. A
state change recomposes only the function that reads the state, not its callers. Composables that return a value
run inside their caller's scope, as Compose requires.

## Editing

Source code is parsed on a background thread after a 150 ms pause in typing. A parse that still has errors keeps the
last working program on screen. Only the new program is interpreted; the composition is kept, so remembered state and
scroll positions survive edits that do not change the structure around them.

## Responsiveness

Interpreted code runs on the UI thread, like compiled Compose code. To keep a host app responsive, the interpreter
stops code that runs for more than 2 seconds without a pause:

- Evaluation steps are counted, and the clock is read every 4,096 steps, so the check costs almost nothing.
- Each entry into interpreted code starts a new 2 seconds: a composition, a draw or layout callback, an event handler,
  or a coroutine resuming after a suspend call that waited (`delay`, `withFrameNanos`). So a continuous animation that
  runs code every frame is never stopped; only one entry that runs too long is. A gap of more than 50 ms between two
  readings also starts a new 2 seconds.
- In plain code, such as an event handler, the code stops with an error. In composition, an error box is shown and
  the current frame ends early.
- The limit is the `maxRunTime` parameter of `Interpreter`; `Duration.INFINITE` turns it off. The benchmark turns it
  off, since it runs its workloads back to back without a pause.

`for` and `while` loops are also limited to 100,000 iterations, and recursion to a depth of 200.

## Limitations of these measurements

- **JVM only.** The numbers are for the JVM after warm-up. Android (ART), JavaScript and WebAssembly have different
  compilers and are not measured yet.
- **Microbenchmarks.** The harness is a simple timed loop, not JMH; garbage collection and JIT activity are not
  controlled. Repeating the benchmark 5 times, and timing binding calls in pairs, limits but does not remove their
  effect.
- **Shared cloud hardware.** GitHub-hosted runners are virtual machines on shared hosts, and the CPU model differs
  between runs of the workflow. An earlier run landed on a newer CPU (AMD EPYC 9V45) and measured the direct
  workloads about twice as fast. Numbers should be compared only within one run of the workflow.
- **Not end to end.** The time from an edit to new pixels on screen, and frame times of real previews compared with
  compiled Compose, are not measured yet.

## Reproducing

Start the [Benchmark workflow](../.github/workflows/benchmark.yml) from the repository's Actions tab, choosing the
number of runs; 6 were used here. The runner's hardware and every run's results appear in the run summary and are
saved as the `benchmark-results` artifact.

The same benchmark runs locally with:

```bash
./gradlew :fast-compose:jvmTest --tests "*InterpreterBenchmarkTest*" -i
```

Each run prints one line per result:

```
benchmark environment: OpenJDK 64-Bit Server VM 21.0.11, Linux amd64, 4 processors, max heap 512 MB; 10 warm-up runs, median of 7 runs, 15 pairs for binding calls
benchmark parsing: median 0.30 ms per parse of 59 lines, 5.015 us per line
benchmark recursion (fib 20): median 14.67 ms, 0.670 us per call (21891 calls)
benchmark function binding call: 0.322 us per call (median of 15 paired differences)
```
