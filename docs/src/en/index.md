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

- [ ] x86-64 Windows Vulkan
- [ ] AArch64 Linux Vulkan
- [ ] WebAssembly Browser WebGPU

### Tier 2

- [ ] x86-64 Linux Vulkan
- [ ] AArch64 Windows Vulkan

## Random ideas

- Everything is an expression.
- If we adopt an LL(1) grammar, autoregressive text models will always be able to generate syntactically correct code by applying logit bias.
  - The property that adding tokens to the end of code with no errors will not introduce errors (except for temporary syntax errors).
- Create a dataset about this language.
  - Fine-tune existing LLMs using this dataset.
- Design an LLM architecture specialized for this language.
- Phase transitions of code:
  1. During editing (LSP requests): perform the computation required for the response.
  2. Before execution: perform runtime checks if necessary.
  3. During execution: run the code.
- Uniqueness / linearity types.
  - Or more generally, fractional / quantitative types.
- Ordered types.
  - There are application examples to type states.
- *n*-bit integer types (signed/unsigned).
- Refinement types that are runtime-checked as needed.
- A UTF-8 string type as a refinement type over a byte array.
- Compile polymorphic code via existentialization.
  - If you want to optimize to the extreme, you can specialize polymorphic code for concrete types via runtime code generation. (runtime monomorphization)
  - Type cases are also possible.
- Recursive functions that do not cause stack overflows.
- Use a memory allocator that understands the language semantics.
- Region-based memory management.
- Reference counting where strong reference cycles are statically prevented.
- Use packed (unaligned) layouts by default.
- Leverage pointer tagging.

## Random resources

- [Using dependent types to express modular structure](https://doi.org/10.1145/512644.512670)
- [Linearity and Uniqueness: An Entente Cordiale](https://doi.org/10.1007/978-3-030-99336-8_13)
- [Functional Ownership through Fractional Uniqueness](https://doi.org/10.1145/3649848)
- [Law and Order for Typestate with Borrowing](https://doi.org/10.1145/3689763)
- [Rows and Capabilities as Modal Effects](https://arxiv.org/abs/2507.10301)
- [Multi-Stage Programming with Splice Variables](https://tsung-ju.org/icfp25/)
- [Compiling Swift Generics](https://download.swift.org/docs/assets/generics.pdf)
- [The Simple Essence of Monomorphization](https://doi.org/10.1145/3720472)
- [Existentialize Your Generics](https://doi.org/10.1145/3759426.3760975)
- [No Graphics API](https://www.sebastianaaltonen.com/blog/no-graphics-api)
