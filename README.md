# OSlang

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

### Round 1 — Core decisions

- [ ] **5.1 Implementation language** — *not yet decided*
- [ ] **5.2 Surface syntax style** — *not yet decided*
- [ ] **5.3 Statement terminator** — *not yet decided*
- [ ] **5.4 Assignment operator** — *not yet decided*

### Round 2 — Type system (Sebesta Ch. 6)

- [ ] **5.5 Primitive types** — *not yet decided*
- [ ] **5.6 Structured type** — *not yet decided*
- [ ] **5.7 Semaphore: primitive or structured?** — *not yet decided*
- [ ] **5.8 Strong typing** — *not yet decided*
- [ ] **5.9 Implicit coercion** — *not yet decided*
- [ ] **5.10 Type equivalence** — *not yet decided*

### Round 3 — Names, binding, scope, lifetime (Sebesta Ch. 5)

- [ ] **5.11 Identifier rules** — *not yet decided*
- [ ] **5.12 Static or dynamic scoping** — *not yet decided*
- [ ] **5.13 Lifetime of variables** — *not yet decided*
- [ ] **5.14 When types are bound** — *not yet decided*

### Round 4 — Expressions (Sebesta Ch. 7)

- [ ] **5.15 Operator precedence** — *not yet decided*
- [ ] **5.16 Associativity** — *not yet decided*
- [ ] **5.17 Short-circuit evaluation** — *not yet decided*
- [ ] **5.18 Operand evaluation order** — *not yet decided*
- [ ] **5.19 Assignment as statement or expression** — *not yet decided*

### Round 5 — Domain-specific constructs

- [ ] **5.20 Process declaration syntax** — *not yet decided*
- [ ] **5.21 Semaphore declaration syntax** — *not yet decided*
- [ ] **5.22 wait/signal syntax** — *not yet decided*
- [ ] **5.23 System declaration syntax** — *not yet decided*
- [ ] **5.24 Scheduler types supported** — *not yet decided*
- [ ] **5.25 run statement syntax** — *not yet decided*
- [ ] **5.26 User-defined functions** — *not yet decided*
- [ ] **5.27 Output commands** — *not yet decided*
- [ ] **5.28 Control flow constructs** — *not yet decided*

### Round 6 — Grammar

- [ ] **5.29 EBNF grammar** — *to be written after all of Round 1–5 is locked*

---

## 6. Example program

*Placeholder. A representative example program will be added once the syntax decisions in Round 5 are locked.*

```
// Example program goes here once syntax is locked.
```

---

## 7. Current status

- [x] Domain chosen (OS simulator DSL)
- [x] Pair confirmed
- [x] Hard scope limits agreed
- [ ] Round 1 decisions locked
- [ ] Round 2 decisions locked
- [ ] Round 3 decisions locked
- [ ] Round 4 decisions locked
- [ ] Round 5 decisions locked
- [ ] EBNF grammar drafted
- [ ] Lexer implemented
- [ ] Parser implemented
- [ ] D1 prose drafted
- [ ] D3 example programs written
- [ ] Part 1 submitted

---

*Last updated: 5 May 2026.*