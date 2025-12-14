# ヿ

ヿ (こと, /koto/) is a heterogeneous systems programming language with first-class types and dynamic type checking.

## High-level goals

- Provide low-level, predictable control suitable for real-time software (operating systems, game engines, audio processors).
- Enable code sharing and interoperability across different execution environments (e.g. CPU/GPU).
- Provide first-class runtime compilation capabilities.
- Minimize the need for users to write proofs by hand where possible.
- Do not make it a theorem prover (logical consistency is not required).
- Do not provide complete static guarantees of program correctness.
- Improve program correctness before execution on a best-effort basis using solvers and property-based testing, complemented by warnings and runtime errors.
- Provide rich editor/IDE support.
- Do not add language features that make reliable editor/tooling support infeasible or brittle.
- Avoid requiring external tools/preprocessors; needing them indicates missing language features.
- Avoid depending on compiler infrastructures such as LLVM or Cranelift.
- Do not provide automatic memory management via tracing GC.

## Example

```koto
type
```

## Supported platforms

### Tier 1

- [ ] x86-64 Linux Vulkan
- [ ] AArch64 Linux Vulkan

### Tier 2

- [ ] WebAssembly Browser WebGPU
- [ ] x86-64 Windows Vulkan
- [ ] AArch64 Windows Vulkan
