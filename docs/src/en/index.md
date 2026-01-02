# ヿ

ヿ (こと, /koto/) is a heterogeneous programming language with first-class types and dynamic type checking.

## High-level goals

- Provide low-level, predictable control suitable for real-time software (game engines, audio processors).
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

```ヿ
# line comment

let zero = 0
let zero : int64 = 0
let b = bool
let t = true
let f = false
let float = float64
let pi = 3.14159
let -pi = -3.14159
let pi : float64 = 3.14159

let pair = 1, 2
let pair : (- : int64), int64 = 1, 2
let pair3 = 1, 2, 3
let pair3 : (- : int64), (- : int64), int64 = 1, 2, 3
let pair3 : (- : int64), ((- : int64), int64) = 1, 2, 3
let pair3 : (- : (- : int64), int64), int64 = (1, 2), 3
let d-pair : (a : type), a = int64, 1
let d-pair3 : (a : type), (- : int64), a = bool, 1, true
let d-pair3 : (a : type), (b : type), a = bool, int64, true
let d-pair3 : (a : type), (b : type), b = bool, int64, 1
let d-pair4 : (a : type), (b : type), (- : a), b = bool, int64, true, 1
let func : (- : int64) → int64 = (x : int64) → x
let func3 : (- : int64) → (- : int64) → int64 = x → y → y
let func3 : (- : int64) → ((- : int64) → int64) = x → y → y
let func3 : (- : (- : int64) → int64) → int64 = f → f(0)
let d-func = (a : type) → a
let d-func3 = (a : type) → (b : type) → b

let refine-type : type = (- : int64) @ true
let refine-type : type = (x : bool) @ x
let refine-trivial : (- : int64) @ true = 1
let refine-compute-success : (x : int64) @ ((y : int64) → true)(x) = 2
let refine-compute-failure : (x : int64) @ ((y : int64) → false)(x) = 3
let refine-singleton : (x : bool) @ x = true

let ite = if true then 1 else 2
let ite : if true then int64 else bool = 1
let ite : if false then int64 else bool = true

let not = (x : bool) → if x then false else true
let and = (x : bool) → (y : bool) → if x then y else false
let or  = (x : bool) → (y : bool) → if x then true else y
let xor = (x : bool) → (y : bool) → if x then if y then false else true else y

# let id = x → x
# let id = x → if x then x : int64 else 0
let id = x → if x then x else x
let id = x → (x : int64)
let id = (x : int64) → x
let id : (- : int64) → int64 = x → x
let id : (- : int64) → int64 = (x : int64) → x
let - : int64 = id(1)

let d-id : (a-t : type) → (a : a-t) → a-t = (a-t : type) → (a : a-t) → a
let - : (- : int64) → int64 = d-id(int64)
let - : int64 = d-id(int64)(3)
let - : bool = d-id(bool)(true)

fun loop(x : int64) → int64 = loop(x)
fun loop-t(a : type) → type = loop-t(a)
# let error-but-not-diverge : loop-t(type) = type

type
```

## Supported platforms

### Tier 1

- [ ] JavaScript Browser WebGPU

### Tier 2

- [ ] WebAssembly Browser WebGPU
- [ ] x86-64 Windows Vulkan

### Tier 3

- [ ] AArch64 Linux Vulkan
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
- Effects as capabilities
    - Capture modality: `let x = 1 [x](y : int64 → x)`
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

- [A Core Language for Extended Pattern Matching and Binding Boolean Expressions](https://www.chargueraud.org/research/2025/bbe/bbe-ml-workshop.pdf)
- [Using dependent types to express modular structure](https://doi.org/10.1145/512644.512670)
- [Type Universes as Kripke Worlds](https://doi.org/10.1145/3747532)
- [Linearity and Uniqueness: An Entente Cordiale](https://doi.org/10.1007/978-3-030-99336-8_13)
- [Functional Ownership through Fractional Uniqueness](https://doi.org/10.1145/3649848)
- [Law and Order for Typestate with Borrowing](https://doi.org/10.1145/3689763)
- [Rows and Capabilities as Modal Effects](https://arxiv.org/abs/2507.10301)
- [Multi-Stage Programming with Splice Variables](https://tsung-ju.org/icfp25/)
- [Compiling Swift Generics](https://download.swift.org/docs/assets/generics.pdf)
- [The Simple Essence of Monomorphization](https://doi.org/10.1145/3720472)
- [Existentialize Your Generics](https://doi.org/10.1145/3759426.3760975)
- [No Graphics API](https://www.sebastianaaltonen.com/blog/no-graphics-api)
