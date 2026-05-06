# OSlang

A small domain-specific language for declaring operating-system concepts — processes, semaphores, and schedulers — and simulating their execution.

> **Course project for CSE 341 (Concepts of Programming Languages), Gebze Technical University, Spring 2026.**
> This document is a living design log. It grows as decisions are locked in. The eventual D1 design specification (the formal PDF deliverable) will be derived from this document.

---

## 1. What this project is

We are designing our own small programming language and writing an interpreter for it. The language is called **OSlang**. It is a domain-specific language (DSL) for describing operating-system simulations.

The course is structured around Sebesta's *Concepts of Programming Languages* (Chapters 1–7). The point of the project is not to build a production language — it is to make real language-design decisions, defend them in the vocabulary of the textbook, and implement them consistently.

This is a **pair project**. Work is split 50/50 across both parts of the project (not necessarily within each part).

---

## 2. What OSlang does

OSlang lets a programmer declare:

- **Processes** with attributes like priority and CPU burst.
- **Semaphores** with initial counter values.
- **Systems** that bundle processes together with a chosen scheduler.

The interpreter then runs the system step by step ("tick by tick") under a chosen scheduling policy. It is a deterministic OS simulator — single-threaded, reproducible, inspectable. Concurrency is *simulated* by interleaving processes under interpreter control, not by actually running threads.

The output of a run is a tick-by-tick trace showing which process ran at each moment, what semaphore operations happened, and which processes blocked or unblocked.

### Why a DSL for this domain?

Operating-system abstractions like ready queues, semaphore wait lists, and context switches are invisible at runtime in real systems. Textbooks teach them with prose and hand-drawn timing diagrams. Heavyweight teaching kernels (xv6, PintOS) require weeks of setup; industrial verification languages (TLA+, Promela) are far beyond an undergraduate course.

OSlang fills the gap: a small declarative language where OS concepts are first-class syntax rather than buried in C structs and pthread calls. The DSL surface makes a producer-consumer setup readable in ten lines instead of fifty.

---

## 3. Hard scope limits — what OSlang is NOT

These exclusions are deliberate. They keep the project achievable and they are themselves a design decision (Sebesta §1.3 — readability/writability/reliability trade-offs).

- **No real concurrency.** The interpreter is single-threaded; concurrency is simulated by interleaving.
- **No memory management.** No malloc, no virtual memory, no pages, no heap.
- **No file system, I/O devices, or networking.**
- **No deadlock detection or formal verification.** A program *can* deadlock — the trace will show all processes blocked — but the language does not *prove* deadlock-freedom.
- **Single CPU.** Exactly one process runs at a time.
- **Bounded.** Maximum ~10 processes per system, finite simulation time.

---

## 4. Project structure and timeline

The project is delivered in two parts, each followed by an in-class written exam with questions personalized to our submission.

| | Submission | Exam |
|---|---|---|
| **Part 1** | Fri 8 May 2026, 23:59 | Thu 14 May 2026, 08:30 |
| **Part 2** | Fri 22 May 2026, 23:59 | Thu 28 May 2026, 08:30 |

**Part 1 covers:** Sebesta Ch. 1–5 design sections + working lexer and parser.
**Part 2 covers:** All of Ch. 1–7 + type checker + interpreter (full execution).

Deliverables per part:

- **D1** — Design specification (PDF)
- **D2** — Implementation source code (zip)
- **D3** — Example programs and test report (PDF)
- **D4** — AI usage journal — *individual, one per partner*
- **D5** — Retrospective — *individual, one per partner*
- **D6** — Contribution report — *pairs only*

---

## 5. Design decisions

This section is filled in as decisions are locked. Each decision will record: the choice made, the one-sentence reason, and the rough Sebesta chapter it relates to. Detailed rationale (for D1 §4.8) will be written separately in our own voice.

---

## PART 1 — Decisions required by 8 May

### Round 1 — Core syntax decisions

- [x] **5.1 Implementation language** — **Java.** Clean class hierarchy for AST nodes; garbage collection removes a class of bugs that would otherwise compete for attention with the language-design work.

- [x] **5.2 Surface syntax style** — **C-family with curly braces.** Block structure is immediately visible; matches the implementation language's mental model; lexer doesn't need indentation tracking.

- [x] **5.3 Statement terminator** — **Semicolon (`;`).** Lexer can ignore whitespace freely; no newline-state to track.

- [x] **5.4 Assignment operator** — **`<-`.** Visually distinct from comparison `==`, eliminates the classic C-family `=` vs `==` confusion. Comparison operators: `==`, `!=`, `<`, `>`, `<=`, `>=`. Lexer uses maximal munch when disambiguating `<-` from `<=` from `<`.

---

### Round 2 — Names, binding, scope, lifetime (Sebesta Ch. 5)

- [x] **5.5 Identifier rules** — Letters, digits, underscore. Must start with a latin letter, must end with a letter or digit. Case insensitive.

  Pattern:
  ```
  [a-zA-Z][a-zA-Z0-9_]*[a-zA-Z0-9]   // length > 1
  [a-zA-Z]                             // length 1 is also valid
  ```

  | Identifier | Legal? |
  |---|---|
  | `producer` | ✅ |
  | `P1` | ✅ |
  | `my_process` | ✅ |
  | `x` | ✅ |
  | `my_process_1` | ✅ |
  | `_internal` | ❌ starts with underscore |
  | `1process` | ❌ starts with digit |
  | `my_process_` | ❌ ends with underscore |
  | `my-process` | ❌ hyphen not allowed |

- [x] **5.6 Scoping** — **Static (lexical) scoping.**
  - Variable lookup is resolved at compile time based on source code structure.
  - Each process body has its own local scope.
  - Functions have their own local scope.
  - Global scope holds semaphore declarations, process declarations, function declarations, system declarations.
  - A process cannot see variables declared inside another process.
  - Exam justification: Sebesta §5.5 — static scoping is predictable, debuggable, and used by all modern languages.

- [x] **5.7 Lifetime of variables** — **Stack-dynamic for locals, static for globals. No heap-dynamic.**

  | Variable type | Lifetime | Declaration |
  |---|---|---|
  | Local variables in process body | Stack-dynamic | `int x <- 0;` inside process |
  | Local variables in functions | Stack-dynamic | `int x <- 0;` inside func |
  | Static variables | Static | `static int counter <- 0;` at top level |
  | Semaphore declarations | Static | `semaphore mutex <- 1;` — implicitly static |
  | Process declarations | Static | `process P1(...) { }` — implicitly static |

  No heap-dynamic — explicitly excluded. No memory management in scope.

  ```
  static int counter <- 0;
  semaphore mutex <- 1;

  process Producer(burst: 3) {
      int temp <- 0;
      temp <- temp + 1;
      counter <- counter + 1;
      post(mutex);
  }
  ```

- [x] **5.8 Type binding** — **Static binding.** Types are bound at declaration and never change.

  Coercion rules:

  | Expression | Result | Rule |
  |---|---|---|
  | `int + int` | `int` | no coercion |
  | `float + float` | `float` | no coercion |
  | `int + float` | `float` | int widened to float — no data loss |
  | `float + int` | `float` | int widened to float — no data loss |
  | `float` assigned to `int` variable | ❌ | compile time error — narrowing not allowed |

  ```
  int x <- 5;
  float y <- 3.14;

  float result <- x + y;   // OK — x widened to float, result is 8.14
  x <- x + y;              // ERROR — cannot assign float to int variable
  ```

---

### Round 3 — Type system (only what the parser needs)

- [x] **5.9 Primitive types** — **`int`, `float`, `bool`, `string`, `semaphore`.** Domain types `semaphore` and `process` are also valid as parameter types in user-defined functions.

  | Type | Example values |
  |---|---|
  | `int` | `0`, `1`, `42` |
  | `float` | `3.14`, `0.5` |
  | `bool` | `true`, `false` |
  | `string` | `"hello"`, `"done"` |
  | `semaphore` | primitive — `semaphore mutex <- 1;` |

- [x] **5.10 Structured type** — **`enum` with implicit int coercion.**

  ```
  enum State {
      ready,      // 0
      running,    // 1
      blocked,    // 2
      finished    // 3
  }

  State s <- ready;    // s is ready (0)
  int x <- blocked;    // x gets 2 — enum to int coercion
  State t <- 1;        // t gets running — int to enum coercion
  ```

  | Coercion | Allowed |
  |---|---|
  | `enum` → `int` | ✅ implicit |
  | `int` → `enum` | ✅ implicit |
  | `enum` → `float` | ❌ not allowed |
  | `float` → `enum` | ❌ not allowed |

  Exam justification: enum-int coercion follows C convention (Sebesta §6.11). Enums are structured types with an underlying integer representation — distinct from primitive-to-primitive coercion rules.

- [x] **5.11 Type categories** — Three categories: primitive, built-in structured, user-defined structured.

  | Type category | Types | User can define new? |
  |---|---|---|
  | Primitive | `int`, `float`, `bool`, `string`, `semaphore` | ❌ built-in |
  | Built-in structured | `process`, `system` | ❌ fixed rules, just declare instances |
  | User-defined structured | `enum` | ✅ user defines |

---

### Round 4 — Expressions (only what the grammar needs)

- [x] **5.12 Operator precedence** — Standard C/Java convention (Sebesta §7.3).

  | Level | Operators | Description |
  |---|---|---|
  | 1 (highest) | `!` `-` (unary) | Unary operators |
  | 2 | `*` `/` `%` | Multiplicative |
  | 3 | `+` `-` | Additive |
  | 4 | `<` `>` `<=` `>=` | Relational |
  | 5 | `==` `!=` | Equality |
  | 6 | `&&` | Logical AND |
  | 7 | `\|\|` | Logical OR |
  | 8 (lowest) | `<-` | Assignment |

- [x] **5.13 Associativity** — All binary operators left-associative. Unary operators right-associative.

  ```
  5 - 3 - 1       // (5 - 3) - 1 = 1
  10 / 2 / 5      // (10 / 2) / 5 = 1
  a && b && c     // (a && b) && c
  !!x             // !(!x) — right to left
  ```

  Exam justification: left-associativity matches mathematical convention and Java/C standard (Sebesta §7.3).

- [x] **5.14 Assignment as statement or expression** — **Statement only. Never an expression.**

  ```
  x <- 5;           // OK — standalone statement
  y <- x <- 5;      // ERROR — chained assignment not allowed
  if (x <- 5) { }   // ERROR — assignment inside condition not allowed
  ```

  Exam justification: assignment as statement only improves reliability (Sebesta §7.7) — eliminates accidental assignment-in-condition bugs, consistent with our choice of `<-` over `=` for clarity.

---

### Round 5 — Domain-specific constructs

- [x] **5.15 Process declaration syntax** — **`process Name(burst: N, priority: N, arrival: N) { body }`**
  - `burst` — **mandatory**, int.
  - `priority` — **optional**, int, default decided in Part 2.
  - `arrival` — **optional**, int, default `0`.
  - Field order flexible. Fields use `:` convention. Statements in body use `<-`.
  - Closed field set — unknown field names are a parse error.
  - Body mandatory syntactically, can be empty.

  ```
  process P1(burst: 3, priority: 2, arrival: 0) {
      wait(mutex);
      x <- x + 1;
      post(mutex);
  }
  ```

- [x] **5.16 Semaphore declaration syntax** — **`semaphore Name <- N;`**
  - Initial value `N` — **mandatory**, non-negative integer.
  - Uses `<-` — single-target binding, consistent with variable assignment.

  ```
  semaphore mutex <- 1;
  semaphore empty <- 5;
  ```

- [x] **5.17 wait/post syntax** — **`wait(s);`** and **`post(s);`**, function-call style, one semaphore argument.
  - `wait(s)` — P operation: decrement if `s > 0`, otherwise block.
  - `post(s)` — V operation: unblock a waiting process or increment `s`.
  - Why `post` not `signal`: `signal` is ambiguous in OS domain (also means UNIX signal handler).

  ```
  wait(mutex);
  x <- x + 1;
  post(mutex);
  ```

- [x] **5.18 System declaration syntax** — **`system Name(processes: [P1, P2, ...], scheduler: SchedType);`**
  - `processes` — **mandatory**, at least one process.
  - `scheduler` — **mandatory**.
  - Dynamic add: `Name.add(P, arrival: N)` — runtime error if add arrival < process declared arrival.
  - `run(Name)` or `run(Name, until: N)` starts simulation.

  ```
  system Sys1(processes: [P1, P2, P3], scheduler: FCFS);
  Sys1.add(P4, arrival: 5);
  run(Sys1, until: 20);
  ```

- [x] **5.19 Scheduler types supported** — **Six built-in schedulers**, embedded in interpreter.

  | Keyword | Full name |
  |---|---|
  | `FCFS` | First Come First Served |
  | `SJF` | Shortest Job First |
  | `SRTF` | Shortest Remaining Time First |
  | `RR` | Round Robin |
  | `PRIORITY` | Priority Scheduling |
  | `MLFQ` | Multi Level Feedback Queue |

  - With parameters: `RR(quant: 2)`, `MLFQ(queues: 3, quant: 2)`.
  - Without parameters: `FCFS`, `SJF`, `SRTF`, `PRIORITY`.

  ```
  system Sys1(processes: [P1, P2], scheduler: FCFS);
  system Sys2(processes: [P1, P2], scheduler: RR(quant: 2));
  system Sys3(processes: [P1, P2], scheduler: MLFQ(queues: 3, quant: 2));
  ```

- [x] **5.20 run statement syntax** — **`run(Name);`** or **`run(Name, until: N);`**
  - `until: N` — optional, stops at clock cycle N.
  - Without `until` — runs until all processes finish, safe max limit decided in Part 2.
  - Why `until` not `ticks`: reads as natural English, avoids confusion with process `burst`.

  ```
  run(Sys1);
  run(Sys1, until: 20);
  ```

- [x] **5.21 User-defined functions** — **`func Name(param: type, ...) -> returnType { body }`**
  - Keyword: `func`.
  - Parameters: explicit types, named with `:` convention.
  - Valid parameter types: `int`, `float`, `bool`, `string`, `semaphore`, `process`.
  - Return type: explicit, after `->`.
  - Return statement: `return` keyword.
  - Top level only — no nested functions.
  - Body can call `wait`, `post`, `print` and access `p.state`, `p.burst` etc.

  ```
  func checkAndWait(s: semaphore, threshold: int) -> bool {
      if (s < threshold) {
          wait(s);
          return true;
      }
      return false;
  }

  func computePriority(burst: int, arrival: int) -> int {
      return burst + arrival;
  }

  func isBlocked(p: process) -> bool {
      return p.state == blocked;
  }
  ```

- [x] **5.22 Output commands** — **`print(expr);`**, function-call style, single argument.
  - Accepts any expression — string, variable, process attribute, semaphore value, current tick.
  - Simulation trace is automatic — printed by interpreter without explicit print calls.

  ```
  print("hello");
  print(x);
  print(tick);
  print(P1.state);
  print(P1.burst);
  print(mutex);
  ```

- [x] **5.23 Control flow constructs** — **`if` / `elif` / `else`** and **`while`**.
  - `elif` and `else` are optional.
  - Parentheses required around conditions.
  - `while` for condition-based loops — chosen over `for` to avoid requiring `for-each`.

  ```
  if (x > 0) {
      print(x);
  } elif (x == 0) {
      print("zero");
  } else {
      print("negative");
  }

  while (x > 0) {
      x <- x - 1;
  }
  ```

---

### Round 6 — Grammar

- [ ] **5.24 EBNF grammar** — *to be written after Rounds 1–5 are locked*

---

## PART 2 — Deferred until after 8 May

- Strong typing rule
- Implicit coercion rules
- Type equivalence (name vs structural)
- Short-circuit evaluation
- Operand evaluation order
- Parameter-passing mode
- Operational semantics for two constructs
- Design rationale paragraphs (D1 §4.8)
- Default value for `priority` when omitted
- Safe maximum tick limit for `run` without `until`
- Detailed scheduler attributes (RR quantum behavior, MLFQ queues, etc.)
- Runtime error details for `add` arrival violation
- `print(s)` output format for enum — prints int value or name?

---

## 6. Example program

```
// Static variable
static int counter <- 0;

// Semaphore declarations
semaphore mutex <- 1;
semaphore empty <- 5;
semaphore full <- 0;

// Enum declaration
enum State {
    ready,
    running,
    blocked,
    finished
}

// User-defined function
func isBlocked(p: process) -> bool {
    return p.state == blocked;
}

// Process declarations
process Producer(burst: 3, priority: 2, arrival: 0) {
    wait(empty);
    wait(mutex);
    counter <- counter + 1;
    post(mutex);
    post(full);
}

process Consumer(burst: 3, priority: 1, arrival: 1) {
    wait(full);
    wait(mutex);
    counter <- counter - 1;
    post(mutex);
    post(empty);
}

// System declaration
system Sys1(processes: [Producer, Consumer], scheduler: RR(quant: 2));

// Run simulation
run(Sys1, until: 20);
```

---

## 7. Current status

**Part 1 (due 8 May)**

- [x] Domain chosen (OS simulator DSL)
- [x] Pair confirmed
- [x] Hard scope limits agreed
- [x] Round 1 — Core syntax decisions locked
- [x] Round 2 — Names/binding/scope/lifetime locked
- [x] Round 3 — Type system locked
- [x] Round 4 — Precedence and associativity locked
- [x] Round 5 — Domain-specific construct syntax locked
- [ ] Round 6 — EBNF grammar drafted
- [ ] Lexer implemented
- [ ] Parser implemented
- [ ] D1 prose drafted (§4.1, §4.2, §4.3, §4.6)
- [ ] D3 example programs written (3 valid + 5 malformed)
- [ ] D4 AI journal — 4 entries logged
- [ ] D5 retrospective written
- [ ] D6 contribution report written
- [ ] Part 1 submitted on Teams

---

*Last updated: 5 May 2026.*