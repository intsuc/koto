# ヿ

ヿ (こと, /koto/) is a heterogeneous systems programming language with first-class types and dynamic type checking.

## High-level goals

- Provide low-level, predictable control suitable for real-time software (operating systems, game engines, audio processors).
- Improve program correctness before execution on a best-effort basis using solvers and property-based testing, complemented by warnings and runtime errors.
- Do not require external preprocessors; needing one indicates missing language features.
- Provide rich editor/IDE support.

## Non-goals

- Being a theorem prover (logical consistency is not required).
- Full static guarantees of program correctness.
- Automatic memory management via tracing garbage collection.
- Language features that make reliable editor/tooling support infeasible or brittle.
