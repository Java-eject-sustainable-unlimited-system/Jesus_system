# Ada JVM

This is a separate Ada project alongside the existing Java watchdog. It is an
early JVM implementation project, **not** a Java 25-compatible JVM yet.

## Current prototype

The first milestone is a small interpreter for raw JVM method bytecode. It
supports `iconst_m1` through `iconst_5`, `bipush`, `iadd`, `isub`, `imul`,
`idiv`, and `ireturn`. It does not read `.class` files, load classes, or
implement the JVM runtime or Java standard library.

The interpreter uses a bounded operand stack and Java `int` arithmetic
semantics for the supported operations. Input files are raw method-code bytes,
not Java class files, and are limited to 64 KiB by the command-line runner.

## Build and run

Install GNAT and `gprbuild`, then from this directory:

```sh
gprbuild -P ada_jvm.gpr
./build/bin/ada_jvm --self-test
./build/bin/ada_jvm method.code
```

`method.code` must contain a supported instruction sequence ending in
`ireturn`. The runner prints the returned integer. `--self-test` runs built-in
interpreter checks.

## Java 25 compatibility roadmap

Full Java 25 compatibility is a substantial runtime implementation, not a
single feature. The prototype will need, at minimum, class-file parsing and
verification, class loading and linking, the complete instruction set and
runtime data areas, exceptions, object and array semantics, native/platform
interfaces, garbage collection, and the Java 25 standard library. These are
not implemented here. Each milestone should add conformance tests before
claiming support.
