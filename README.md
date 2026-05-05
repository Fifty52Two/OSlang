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

Decisions are organized by **Part 1 (due 8 May)** and **Part 2 (due 22 May)**. We are currently focused on Part 1. Part 2 decisions are listed at the end of this section for reference but will be addressed after the Part 1 deadline.

---

## PART 1 — Decisions required by 8 May

### Round 1 — Core syntax decisions

These shape every line of code that follows.

- [x] **5.1 Implementation language** — **Java.** Clean class hierarchy for AST nodes; garbage collection removes a class of bugs that would otherwise compete for attention with the language-design work.
- [x] **5.2 Surface syntax style** — **C-family with curly braces.** Block structure is immediately visible; matches the implementation language's mental model; lexer doesn't need indentation tracking.
- [x] **5.3 Statement terminator** — **Semicolon (`;`).** Lexer can ignore whitespace freely; no newline-state to track.
- [x] **5.4 Assignment operator** — **`<-`.** Visually distinct from comparison `==`, eliminates the classic C-family `=` vs `==` confusion, and gives the language a small originality marker. Comparison operators are the standard C-family set: `==`, `!=`, `<`, `>`, `<=`, `>=`. Note: lexer uses maximal munch when disambiguating `<-` from `<=` from `<`.

---

### Round 2 — Names, binding, scope, lifetime (Sebesta Ch. 5)

Required by D1 §4.6 (graded section of the Part 1 design spec).

- [ ] **5.5 Identifier rules** — *not yet decided*
- [ ] **5.6 Static or dynamic scoping** — *not yet decided*
- [ ] **5.7 Lifetime of variables** — *not yet decided*
- [ ] **5.8 When types are bound** — *not yet decided*

---

### Round 3 — Type system (only what the parser needs)

Full type-system rules are a Part 2 deliverable. For Part 1 we only need to know *which types exist* so the lexer can recognize their keywords and the parser can handle declarations.

- [ ] **5.9 Primitive types** — *not yet decided*
- [ ] **5.10 Structured type** — *not yet decided*
- [ ] **5.11 Is Semaphore a primitive or structured type** — *not yet decided*

---

### Round 4 — Expressions (only what the grammar needs)

Full expression semantics are a Part 2 deliverable. For Part 1 the parser must encode operator precedence and associativity, so these are needed now.

- [ ] **5.12 Operator precedence** — *not yet decided*
- [ ] **5.13 Associativity** — *not yet decided*
- [ ] **5.14 Assignment as statement or expression** — *not yet decided*

---

### Round 5 — Domain-specific constructs

The exact concrete syntax for each construct. Without this, the grammar can't be written.

- [x] **5.15 Process declaration syntax** — **`process Name(burst: N, priority: N, arrival: N) { body }`**.
  - `burst` — **mandatory**, int. Simulator cannot run without knowing how long a process runs.
  - `priority` — **optional**, int, default decided in Part 2.
  - `arrival` — **optional**, int, default `0`. Process is ready from tick 0 if omitted.
  - Field order is **flexible** — fields are named so order does not matter.
  - Body is **mandatory** syntactically, but can be empty — parser allows it.
  - Fields inside `( )` use `:` (named-field convention). Statements inside `{ }` use `<-` for assignment.
  - Closed field set — any unknown field name is a parse error, catching typos early.

  ```
  process P1(burst: 3, priority: 2, arrival: 0) {
      wait(mutex);
      x <- x + 1;
      post(mutex);
  }
  ```

- [x] **5.16 Semaphore declaration syntax** — **`semaphore Name <- N;`**.
  - Initial value `N` is a **mandatory** non-negative integer.
  - Uses `<-` because this is a single-target binding (one name, one value, on its own line) — consistent with variable assignment.
  - `:` is reserved for named fields inside multi-field lists (process parameters, system parameters).

  ```
  semaphore mutex <- 1;
  semaphore empty <- 5;
  ```

- [x] **5.17 wait/post syntax** — **`wait(s);`** and **`post(s);`**, function-call style, one semaphore argument.
  - `wait(s)` — P operation: if `s > 0`, decrement and continue; otherwise block the calling process on `s`'s waiting queue.
  - `post(s)` — V operation: if any process is blocked on `s`, unblock one; otherwise increment `s`.
  - Why `post` not `signal`: `signal` is overloaded in OS — it also refers to UNIX signal handlers (`SIGINT`, `SIGKILL`), causing ambiguity inside the OS domain itself.
  - Why `post` not `release`: follows POSIX (`sem_wait` / `sem_post`), well established in systems programming.

  ```
  wait(mutex);
  x <- x + 1;
  post(mutex);
  ```

- [x] **5.18 System declaration syntax** — **`system Name(processes: [P1, P2, ...], scheduler: SchedType);`**.
  - `processes` — **mandatory**, bracketed comma-separated list of process identifiers. At least one process required.
  - `scheduler` — **mandatory**, scheduler type with optional parameters (see 5.19).
  - Both fields use `:` (named-field convention). Field order flexible.
  - Semicolon-terminated, no body block — a system has no code of its own.
  - Processes can be added dynamically after declaration using `Name.add(P, arrival: N)`.
  - **Dynamic add rule:** if `arrival` in `add` call is less than the process's declared `arrival`, a runtime error is raised. Equal or greater is allowed. (Runtime check — Part 2 concern.)
  - `run(Name)` starts the simulation. `run(Name, until: N)` stops at clock cycle N.

  ```
  system Sys1(processes: [P1, P2, P3], scheduler: FCFS);
  Sys1.add(P4, arrival: 5);
  run(Sys1);
  run(Sys1, until: 20);
  ```

- [x] **5.19 Scheduler types supported** — **Six built-in schedulers.** Schedulers are embedded in the interpreter — users select by name, they do not define scheduling logic.

  | Keyword | Full name | Required attribute |
  |---|---|---|
  | `FCFS` | First Come First Served | `arrival` |
  | `SJF` | Shortest Job First | `burst` |
  | `SRTF` | Shortest Remaining Time First | `burst` |
  | `RR` | Round Robin | `quant` on scheduler |
  | `PRIORITY` | Priority Scheduling | `priority` |
  | `MLFQ` | Multi Level Feedback Queue | `quant`, scheduler params |

  - Schedulers that need parameters use function-call style: `RR(quant: 2)`, `MLFQ(queues: 3, quant: 2)`.
  - Schedulers with no parameters use bare name: `FCFS`, `SJF`, `SRTF`, `PRIORITY`.
  - Detailed scheduler attributes deferred to Part 2.

  ```
  system Sys1(processes: [P1, P2], scheduler: FCFS);
  system Sys2(processes: [P1, P2], scheduler: RR(quant: 2));
  system Sys3(processes: [P1, P2], scheduler: PRIORITY);
  system Sys4(processes: [P1, P2], scheduler: SJF);
  system Sys5(processes: [P1, P2], scheduler: SRTF);
  system Sys6(processes: [P1, P2], scheduler: MLFQ(queues: 3, quant: 2));
  ```

- [x] **5.20 run statement syntax** — **`run(Name);`** or **`run(Name, until: N);`**.
  - System name — **mandatory**.
  - `until: N` — **optional**. Stops simulation at clock cycle N.
  - If `until` is omitted, simulation runs until all processes finish. A safe maximum tick limit is applied by the interpreter to prevent infinite loops in deadlock scenarios (exact value decided in Part 2).
  - Why `until` not `ticks`: reads as natural English — "run until cycle 20" — and avoids confusion with process `burst` which is also measured in ticks.

  ```
  run(Sys1);              // runs until all processes finish
  run(Sys1, until: 20);   // stops at clock cycle 20
  ```

- [ ] **5.21 User-defined functions** — *not yet decided*
- [ ] **5.22 Output commands** — *not yet decided*
- [ ] **5.23 Control flow constructs** — *not yet decided*

---

### Round 6 — Grammar

- [ ] **5.24 EBNF grammar** — *to be written after Rounds 1–5 are locked*

---

## PART 2 — Deferred until after 8 May

These are required for the final D1 spec (due 22 May) and the type checker / interpreter implementation. They do not affect the Part 1 lexer or parser, so we're explicitly setting them aside for now.

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
- Detailed scheduler attributes (MLFQ queues, RR quantum behavior, etc.)
- Runtime error details for `add` arrival violation

---

## 6. Example program

```
// Semaphore declarations
semaphore mutex <- 1;
semaphore empty <- 5;
semaphore full <- 0;

// Process declarations
process Producer(burst: 3, priority: 2, arrival: 0) {
    wait(empty);
    wait(mutex);
    x <- x + 1;
    post(mutex);
    post(full);
}

process Consumer(burst: 3, priority: 1, arrival: 1) {
    wait(full);
    wait(mutex);
    x <- x - 1;
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
- [ ] Round 2 — Names/binding/scope/lifetime locked
- [ ] Round 3 — Primitive and structured types locked
- [ ] Round 4 — Precedence and associativity locked
- [~] Round 5 — Domain-specific construct syntax (5.15–5.20 locked, 5.21–5.23 remaining)
- [ ] EBNF grammar drafted
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