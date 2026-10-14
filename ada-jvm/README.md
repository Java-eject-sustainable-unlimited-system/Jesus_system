# Ada JVM

This is a separate Ada project alongside the existing Java watchdog. It is an
early JVM implementation project, **not** a Java 25-compatible JVM yet.

## Current prototype

The Ada prototype interprets a small subset of JVM method bytecode. It supports
`iconst_m1` through `iconst_5`, `bipush`, `iadd`, `isub`, `imul`, `idiv`, and
`ireturn`. It can also parse a Java class file and extract a selected static
method's `Code` attribute before interpreting it. Class loading, method
invocation, and the JVM runtime or Java standard library are not implemented.

The interpreter uses a bounded operand stack and Java `int` arithmetic
semantics for the supported operations. Raw method-code files are limited to
64 KiB by the command-line runner.

## Build and run

Install GNAT and `gprbuild`, then from this directory:

```sh
gprbuild -P ada_jvm.gpr
./build/bin/ada_jvm --self-test
./build/bin/ada_jvm method.code
```

`method.code` must contain a supported instruction sequence ending in
`ireturn`. To execute a supported method from a class file, provide its name
and JVM descriptor, for example:

```sh
./build/bin/ada_jvm --class-file Example.class calculate '()I'
```

Class files are limited to 16 MiB, and method bytecode to 65535 bytes. The
reader accepts class-file versions 45 through 69 and extracts static methods
only. It does not verify bytecode, resolve classes, or execute the complete JVM
instruction set. The runner prints the returned integer. `--self-test` runs
built-in interpreter checks.

## Rust bytecode interpreter

`rust-vm/` contains a dependency-free Rust implementation of the same integer
bytecode subset. It is a companion interpreter, not an FFI backend for the Ada
program. Rust's safe slice access and fixed-size operand stack avoid unsafe
memory operations in this interpreter; this does not make the Ada components
memory-safe or verify untrusted bytecode.

Install Rust and Cargo, then from this directory:

```sh
cargo test --locked --manifest-path rust-vm/Cargo.toml
cargo run --locked --manifest-path rust-vm/Cargo.toml -- method.code
```

The Rust runner accepts raw method-code bytes only. Class-file parsing and
static-method extraction remain available through the Ada runner, not Rust.
Both implementations are limited prototypes and do not constitute a complete
or Java-compatible JVM.

## Java-hosted integration

`com.jesus.system.jvm.AdaRustJvmBridge` lets Java code invoke both interpreters
and compare their integer results for the shared bytecode subset. It also lets
Java invoke Ada's static-method class-file reader. The bridge starts the native
executables as separate processes; it is not JNI, and it does not make either
interpreter part of the Java Virtual Machine implementation.

Set `JESUS_ADA_VM` and `JESUS_RUST_VM` to the executable paths before calling
`AdaRustJvmBridge.fromEnvironment()`. Raw bytecode is limited to 64 KiB and
interpreter processes have a 10-second timeout. The Nix Linux package supplies
both paths automatically. CI builds both interpreters first, then runs Java
integration tests for arithmetic and Ada class-file extraction.

## Java 25 compatibility roadmap

The target is **Java SE 25 runtime compatibility**, including the Java Virtual
Machine Specification (JVMS) and the Java SE 25 API specification. This project
is a runtime, not a Java source compiler: accepting Java 25 source syntax is
outside its scope. Compatibility includes class-file versions and features,
verification, linking and execution semantics, and the specified Java SE APIs;
support for only the class-file major version or a subset of bytecodes is not
full compatibility.

### Conformance strategy

Build a conformance suite from small Java programs and class files compiled
with a Java 25 JDK. Run each supported fixture both on this runtime and on an
independent Java 25 reference JVM, then compare specified observable behavior:
return values, output, exit status, and exception behavior. Include valid
fixtures as well as malformed or unsupported inputs, which must be rejected
safely rather than misinterpreted. Keep fixtures and expected outcomes
reproducible, and organize them by JVMS and Java SE API feature so gaps remain
visible. Use the official Java compatibility test suite when its terms and
availability permit; a local differential suite alone is not a certification.

Do not claim Java 25 compatibility until the implementation covers the defined
runtime and API scope and passes the applicable conformance suite. The current
prototype does not meet that bar: verification, class loading and linking,
most instructions, runtime data areas, exceptions, objects and arrays,
platform interfaces, garbage collection, and the Java SE library remain
unimplemented. Implementation and conformance testing must advance together
through these milestones.
