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

- [x] **5.5 Identifier rules** — Letters, digits, underscore. Must start with a latin letter, must end with a letter or digit. **Case-sensitive** — `Producer` and `producer` are distinct identifiers.

  Pattern:
  ```
  [a-zA-Z][a-zA-Z0-9_]*[a-zA-Z0-9]   // length > 1
  [a-zA-Z]                             // length 1 is also valid
  ```

  | Identifier | Legal? |
  |---|---|
  | `producer` | ✅ |
  | `Producer` | ✅ — distinct from `producer` |
  | `P1` | ✅ |
  | `my_process` | ✅ |
  | `x` | ✅ |
  | `my_process_1` | ✅ |
  | `_internal` | ❌ starts with underscore |
  | `1process` | ❌ starts with digit |
  | `my_process_` | ❌ ends with underscore |
  | `my-process` | ❌ hyphen not allowed |

  Exam justification: case-sensitivity follows the C-family tradition (Java, C, C#) we have already committed to with curly-brace syntax (5.2) and aligns with Sebesta §5.2's note that modern languages favor case-sensitive identifiers because predefined names use mixed case.

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

- [x] **5.10 Structured type** — **`enum` as first-class type in variable declarations, with two-layer range enforcement.**

  **Enum declaration syntax:**
  ```
  enum Name {
      member1,
      member2,
      member3
  }
  ```

  - `enum` keyword, then a name, then a brace-delimited list of comma-separated member identifiers.
  - Each member is implicitly assigned a non-negative integer starting from `0` in declaration order.
  - At least one member is required.
  - Trailing comma after the last member is **not** allowed.
  - The enum name and member names follow the standard identifier rules (5.5) and are case-sensitive.
  - Enum declarations are top-level only — they cannot appear inside process or function bodies.
  - Member names live in the global namespace — collisions with other identifiers are a compile-time error.

  **Enum types are first-class in variable and static declarations.** `State s <- ready;` is legal. The grammar accepts any `IDENT` as a type name in `<var_decl_stmt>` and `<static_decl>`; the type checker (Part 2) validates that the name refers to a declared enum.

  ```
  enum State {
      ready,      // 0
      running,    // 1
      blocked,    // 2
      finished    // 3
  }

  State s <- ready;       // ✅ enum member assigned to enum variable
  State t <- s;           // ✅ enum-to-enum assignment — same type
  int x <- blocked;       // ✅ enum to int — widening, always safe (x gets 2)
  State u <- 1;           // ✅ compiles — int-to-enum coercion, checked at runtime (u gets running)
  State bad <- 99;        // ❌ COMPILE-TIME ERROR — literal 99 is out of range [0, 3]
  ```

  **Two-layer range enforcement for int-to-enum assignments (Sebesta §6.13, §6.14):**

  | Right-hand side | When checked | Rule |
  |---|---|---|
  | Enum member (`ready`, `blocked`, …) | — | Always valid; no check needed |
  | Enum variable of the same type | — | Always valid; same range guaranteed |
  | `INT_LIT` | **Compile time** | Rejected if literal ∉ `[0, memberCount-1]` |
  | Integer expression or variable | **Runtime** | Error thrown if evaluated value ∉ `[0, memberCount-1]` |

  **Type equivalence for enums — name equivalence (Sebesta §6.15).** Two enum types are compatible only if they have the same declared name. `Day` and `Month` are different types even if they have the same number of members. `Day d <- aMonthVar;` is a compile-time type error.

  | Assignment | Result |
  |---|---|
  | `EnumType var <- enumMember` | ✅ always valid |
  | `EnumType var <- sameTypeVar` | ✅ always valid |
  | `EnumType var <- differentEnumVar` | ❌ compile-time type error |
  | `EnumType var <- INT_LIT` in range | ✅ valid |
  | `EnumType var <- INT_LIT` out of range | ❌ compile-time error |
  | `EnumType var <- intExpression` | ✅ compiles; runtime error if out of range |
  | `int var <- enumValue` | ✅ always valid — widening, no data loss |
  | `EnumType var <- floatAnything` | ❌ compile-time error |
  | `float var <- enumValue` | ❌ compile-time error |

  Exam justification: Sebesta §6.4.1 identifies range validity as a core enum design issue. §6.13 establishes that type errors must be detected either statically or dynamically for strong typing. §6.14 notes that coercion weakens strong typing — we limit int-to-enum coercion to cases where the value is provably valid (literal) or checked at runtime (expression), which is a deliberate trade-off between writability and reliability (§1.6). §6.15 name equivalence prevents cross-enum assignment bugs that structural equivalence would silently allow.

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
  - `processes` — **mandatory**, at least one process. Empty `processes: []` is a parse error (a processless system has nothing to simulate).
  - `scheduler` — **mandatory**.
  - **Field order is fixed:** `processes` first, then `scheduler`. This differs from `process_decl` (§5.15) where field order is flexible. Rationale: a system declaration is a top-level configuration, so a uniform shape across the codebase is more important than writing flexibility (Sebesta §1.3.3 — readability favoured over writability for declarative configuration).
  - Dynamic add: `Name.add(P, arrival: N)` — runtime error if add arrival < process declared arrival.
  - `run(Name)` or `run(Name, until: N)` starts simulation.

  ```
  system Sys1(processes: [P1, P2, P3], scheduler: FCFS);
  Sys1.add(P4, arrival: 5);
  run(Sys1, until: 20);
  ```

- [x] **5.19 Scheduler types supported** — **Five built-in schedulers**, reserved keywords (users cannot name a variable `FCFS`, `RR`, etc.).

  | Keyword | Full name | Parameters |
  |---|---|---|
  | `FCFS` | First Come First Served | none |
  | `SJF` | Shortest Job First | none |
  | `SRTF` | Shortest Remaining Time First | none |
  | `RR` | Round Robin | `quant: INT_LIT` (mandatory) |
  | `PRIORITY` | Priority Scheduling (non-preemptive) | none |

  Only `RR` takes a parameter, because round-robin is undefined without a quantum. The other schedulers have no tunable knob in their textbook definition. `PRIORITY` is fixed as **non-preemptive** in OSlang — once a process starts, it runs to completion, even if a higher-priority process arrives later. This is a deliberate language-level decision rather than a per-system flag.

  `quant` accepts an **integer literal only** (not a general expression). Rationale: the quantum is a static configuration parameter, conceptually constant per system, not a value the program computes. Allowing arbitrary expressions would suggest dynamic schedulers, which OSlang doesn't have. Sign and magnitude (`quant > 0`) are checked by the type checker — EBNF cannot express them.

  ```
  system Sys1(processes: [P1, P2], scheduler: FCFS);
  system Sys2(processes: [P1, P2], scheduler: RR(quant: 2));
  system Sys3(processes: [P1, P2], scheduler: PRIORITY);
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
  - Valid parameter types: `int`, `float`, `bool`, `string`, `semaphore`, `process`. Enum types are first-class in variable declarations (§5.10) but **not yet in function parameter or return types** — that is deferred to Part 2. To pass an enum value into a function, declare the parameter as `int` and rely on the enum→int widening coercion from §5.10.
  - Valid return types: `int`, `float`, `bool`, `string`. Returning a `semaphore` or `process` reference is not allowed.
  - Return type: explicit, after `->`.
  - Return statement: `return` keyword.
  - Top level only — no nested functions.
  - Empty parameter list is allowed: `func tick() -> int { ... }`.
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

- [x] **5.24 EBNF grammar** — *being drafted step by step. EBNF metasymbols follow Sebesta §3.3.2: `[ ]` optional, `{ }` zero-or-more repetition, `( ... | ... )` grouped alternatives. Where braces are used to express left-associative operator chains, associativity is not implied by the grammar itself (Sebesta §3.3.2, p.127) — it is enforced by the parser, consistent with our decision in §5.13.*

  **Step 1 — Top-level structure (LOCKED)**

  A program is one or more top-level items. An empty source file is a syntax error — every OSlang program must contain at least one declaration or executable statement. Ordering between declarations and executable statements (e.g., `run` after declarations) is a semantic concern handled by the type checker, not the grammar.

  ```
  <program>        ::= <top_level_decl> { <top_level_decl> }

  <top_level_decl> ::= <static_decl>
                     | <semaphore_decl>
                     | <enum_decl>
                     | <process_decl>
                     | <func_decl>
                     | <system_decl>
                     | <add_stmt>
                     | <run_stmt>
  ```

  **Step 2 — Declarations (in progress)**

  Type rules — four context-specific type categories. `<decl_type>` is used in variable and static declarations and accepts enum names as well as simple types. `<param_type>` and `<return_type>` remain restricted to built-in types for now — enum types in function signatures are deferred to Part 2.

  ```
  <simple_type>    ::= "int" | "float" | "bool" | "string"

  <decl_type>      ::= <simple_type> | IDENT
                    // IDENT in type position is validated by the type checker
                    // as a declared enum name — the grammar is permissive here

  <param_type>     ::= <simple_type> | "semaphore" | "process"

  <return_type>    ::= <simple_type>
  ```

  `static_decl` and `semaphore_decl` — initial value is any expression at the grammar level; type/sign constraints (e.g., semaphore initial value must be a non-negative int) are checked by the type checker.

  ```
  <static_decl>    ::= "static" <decl_type> IDENT "<-" <expr> ";"

  <semaphore_decl> ::= "semaphore" IDENT "<-" <expr> ";"
  ```

  `enum_decl` — at least one member; trailing comma not allowed; no semicolon after `}`.

  ```
  <enum_decl>      ::= "enum" IDENT "{" <enum_members> "}"

  <enum_members>   ::= IDENT { "," IDENT }
  ```

  `process_decl` header — closed field set encoded in the grammar (unknown field names are a parse error per §5.15). Three constraints are deferred to the type checker because EBNF cannot express them: `burst` mandatory, no duplicate fields, positive integer values.

  ```
  <process_decl>       ::= "process" IDENT "(" <process_fields> ")" <block>

  <process_fields>     ::= <process_field> { "," <process_field> }

  <process_field>      ::= <process_field_name> ":" <expr>

  <process_field_name> ::= "burst" | "priority" | "arrival"
  ```

  `func_decl` header — empty parameter list allowed; return type mandatory.

  ```
  <func_decl>      ::= "func" IDENT "(" [ <param_list> ] ")" "->" <return_type> <block>

  <param_list>     ::= <param> { "," <param> }

  <param>          ::= IDENT ":" <param_type>
  ```

  `system_decl` — fixed field order (`processes` then `scheduler`); both fields mandatory; at least one process; scheduler names are reserved keywords. Fixing the order makes the grammar enforce all four constraints with no work left for the type checker — a real win compared to `process_decl` where flexible order forced us to defer checks.

  ```
  <system_decl>    ::= "system" IDENT "(" "processes" ":" "[" <process_list> "]"
                                          "," "scheduler" ":" <scheduler>
                                      ")" ";"

  <process_list>   ::= IDENT { "," IDENT }

  <scheduler>      ::= "FCFS"
                     | "SJF"
                     | "SRTF"
                     | "PRIORITY"
                     | "RR" "(" "quant" ":" INT_LIT ")"
  ```

  **Step 2 — Declarations (LOCKED)**

  **Step 3 — Expressions (LOCKED)**

  Bottom-up precedence cascade following §5.12 — lowest precedence (outermost) first, highest precedence (innermost) last. Sebesta §3.3.2 (p.127) note: `{ }` repetition does NOT encode left-associativity; the parser folds left per our §5.13 decision. Unary right-associativity (`!!x` parses as `!(!x)`) is encoded by `<unary_expr>` recursing on itself.

  ```
  <expr>                ::= <or_expr>

  <or_expr>             ::= <and_expr> { "||" <and_expr> }

  <and_expr>            ::= <equality_expr> { "&&" <equality_expr> }

  <equality_expr>       ::= <relational_expr> { ( "==" | "!=" ) <relational_expr> }

  <relational_expr>     ::= <additive_expr> { ( "<" | ">" | "<=" | ">=" ) <additive_expr> }

  <additive_expr>       ::= <multiplicative_expr> { ( "+" | "-" ) <multiplicative_expr> }

  <multiplicative_expr> ::= <unary_expr> { ( "*" | "/" | "%" ) <unary_expr> }

  <unary_expr>          ::= ( "!" | "-" ) <unary_expr>
                          | <postfix_expr>

  <postfix_expr>        ::= <primary> [ "." <process_attr> ]

  <process_attr>        ::= "state" | "burst" | "priority" | "arrival"

  <primary>             ::= <literal>
                          | IDENT [ "(" [ <arg_list> ] ")" ]
                          | "(" <expr> ")"

  <arg_list>            ::= <expr> { "," <expr> }

  <literal>             ::= INT_LIT | FLOAT_LIT | BOOL_LIT | STRING_LIT
  ```

  Decisions baked into this:
  - **Numeric literals are unsigned at the token level.** `INT_LIT` is digits only (e.g. `42`); negatives are formed by the unary `-` operator. `FLOAT_LIT` requires digits on both sides of the dot (e.g. `3.14`, `0.5`) — never `.5` or `5.`. This avoids ambiguity with the `.` in `p.state` and keeps the lexer simple.
  - **Function calls are merged into `<primary>`.** A bare `IDENT` is a variable reference; `IDENT(...)` is a function call. Same parse path until the parser sees `(`.
  - **`wait`, `post`, `print` are not special in the grammar.** They are ordinary function calls; the language defines them as built-in. The "this name is a built-in, not a user-defined function" check is a semantic concern.
  - **Postfix dot-access is one level deep, closed attribute set.** Only `state`, `burst`, `priority`, `arrival` follow a `.`. Anything else is a parse error. `a.b.c` does not parse. There are no general method calls — `Sys1.add(...)` is a separate top-level statement form, not an instance of method-call syntax.

  **Step 4 — Statements (LOCKED)**

  A `<block>` is a brace-enclosed sequence of zero or more statements (matches §5.15 — process bodies are mandatory syntactically but can be empty). Statements come in six kinds.

  ```
  <block>          ::= "{" { <statement> } "}"

  <statement>      ::= <var_decl_stmt>
                     | <assign_stmt>
                     | <call_stmt>
                     | <if_stmt>
                     | <while_stmt>
                     | <return_stmt>

  <var_decl_stmt>  ::= <decl_type> IDENT "<-" <expr> ";"

  <assign_stmt>    ::= IDENT "<-" <expr> ";"

  <call_stmt>      ::= IDENT "(" [ <arg_list> ] ")" ";"

  <return_stmt>    ::= "return" <expr> ";"

  <while_stmt>     ::= "while" "(" <expr> ")" <block>

  <if_stmt>        ::= "if" "(" <expr> ")" <block>
                       { "elif" "(" <expr> ")" <block> }
                       [ "else" <block> ]
  ```

  Decisions baked into this:
  - **Local variable declarations require an initializer.** `int x;` does not parse; `int x <- 0;` does. Sebesta §5.4.2 (reliability argument against implicit/incomplete declarations) and §5.4.3.2 (initialization is part of stack-dynamic elaboration) both support this. Modern precedent: C# `var`, Rust `let` (with caveats), Kotlin `val`/`var` with explicit initializers.
  - **Assignment, function-call-as-statement, and expressions are syntactically distinct rules.** This implements §5.14 ("assignment is a statement only, never an expression") at the grammar level rather than after parsing. Defense: Sebesta §7.7.5 documents the bugs that arise when assignment is treated as an expression (the classic `if (x = y)` typo). Keeping these as separate productions makes the design decision visible in the grammar.
  - **`return` always carries an expression.** No bare `return;`. Because §5.21 declares every function has a non-void return type, a bare `return;` would syntactically permit a function to violate its own type signature.
  - **Dangling-else does not arise.** Sebesta §3.3.1.4 shows the canonical if-else ambiguity for languages where statements after `if`/`else` can be bare. OSlang sidesteps this entirely: every branch body is a mandatory `<block>` (always braced). No `<matched>`/`<unmatched>` non-terminal split is needed. Same approach as Swift, Rust, Go.
  - **`elif` is a single keyword token**, not `else if`. Matches §5.23 and the Python convention. Saves a parse path.

  **Step 5 — Top-level executable statements (LOCKED)**

  Two operations on already-declared systems. Both are top-level only — they appear among `<top_level_decl>` alternatives, not inside any block. Reserved keywords (`run`, `add`) keep them grammar-special; they cannot be redefined or shadowed by user identifiers.

  ```
  <add_stmt>  ::= IDENT "." "add" "(" IDENT "," "arrival" ":" <expr> ")" ";"

  <run_stmt>  ::= "run" "(" IDENT [ "," "until" ":" <expr> ] ")" ";"
  ```

  Decisions baked into this:
  - **Both `arrival` and `until` accept `<expr>`**, not `INT_LIT`. Rationale: these are runtime values that may sensibly come from a variable (e.g. `until: maxTicks` where `maxTicks` is a `static int`). Only `quant` (in `<scheduler>`) is restricted to `INT_LIT`, because a scheduler quantum is a static configuration knob, not a runtime value.
  - **`run` is a reserved keyword.** It cannot appear anywhere except as the start of `<run_stmt>`. A user cannot have a variable, function, or process named `run`. This is stricter than how `wait`/`post`/`print` are treated (those are ordinary identifiers that the language defines as built-ins), and it reflects that `run` has exactly one role in the language: starting a simulation at top level.
  - **`add` is also a reserved keyword in this grammar** (it appears as the literal `"add"` in `<add_stmt>`). Same reasoning — `Sys1.add(...)` is a special, narrowly-scoped operation, not a general method call.

  **Reserved keywords accumulated across the grammar.** The lexer must recognize these as their own token classes, not as identifiers:

  - **Types and storage:** `int`, `float`, `bool`, `string`, `semaphore`, `process`, `system`, `enum`, `static`, `func`
  - **Boolean literals:** `true`, `false` — recognized by the lexer as `BOOL_LIT` tokens, not grammar-level keywords (see Issue 4 resolution above)
  - **Control flow:** `if`, `elif`, `else`, `while`, `return`
  - **Built-in operations:** `wait`, `post`, `print` (treated as ordinary identifiers in the grammar — the language defines them as built-ins)
  - **Scheduler names:** `FCFS`, `SJF`, `SRTF`, `RR`, `PRIORITY`
  - **Field names (as keywords in their host rules):** `processes`, `scheduler`, `quant`, `burst`, `priority`, `arrival`, `until`
  - **Process attribute names:** `state` (also `burst`, `priority`, `arrival` — overloaded with field names but unambiguous because of the leading `.`)
  - **Top-level operations:** `run`, `add`

  **Step 6 — Review pass (LOCKED — all 4 issues resolved)**

  A full end-to-end audit of the grammar found four issues. All four are now resolved. The grammar shape (Steps 1–5) is sound — no ambiguities, no orphan rules, no undefined references, follows Sebesta §3.3.2 conventions, dangling-else avoided via mandatory `<block>`.

  **Issue 1 — Enum types in declarations. RESOLVED: Option C (first-class in declarations).**

  Enum types are now first-class in `<var_decl_stmt>` and `<static_decl>` via the new `<decl_type>` rule. `State s <- ready;` is legal. The §5.10 examples have been updated accordingly. Enum types remain excluded from `<param_type>` and `<return_type>` — deferred to Part 2. Exam justification: Sebesta §6.4.2 — using plain `int` to simulate enums eliminates type checking; making enum a proper type restores it.

  **Issue 2 — `STRING_LIT` was undefined. RESOLVED.**

  String literals use double-quote delimiters. Any character except `"` and newline is allowed unescaped. Supported escape sequences: `\"`, `\\`, `\n`, `\t`. Multi-line strings are not supported — a newline inside a string literal is a lexer error. Rationale: single-line restriction means an unclosed quote is caught on the same line, improving reliability (Sebesta §1.3). The lexer rule:

  ```
  STRING_LIT  ::=  '"'  { <str_char> }  '"'
  <str_char>  ::=  any character except '"' and '\n'
               |   '\' ( '"' | '\' | 'n' | 't' )
  ```

  `STRING_LIT` is a lexer-level rule only. The EBNF grammar references it as a terminal in `<literal>`.

  **Issue 3 — Comments were missing. RESOLVED.**

  Single-line comments only: `//` introduces a comment that extends to the end of the line. Multi-line `/* ... */` block comments are not supported — they would add a state to the lexer for no gain in a language this small (same rationale as Python's single-comment-style choice). Comments are stripped by the lexer; the parser never sees them.

  ```
  // This is a valid comment — everything after // until newline is ignored
  semaphore mutex <- 1;  // inline comment also valid
  ```

  **Issue 4 — `BOOL_LIT` keyword vs. token. RESOLVED: Option A (lexer token).**

  `true` and `false` are recognized by the lexer and emit a `BOOL_LIT` token with the boolean value. They are **not** quoted strings in the EBNF grammar — they are handled identically to `INT_LIT` and `FLOAT_LIT`. The lexer checks identifiers against the keyword table; when it matches `true` or `false`, it emits `BOOL_LIT` rather than `IDENT`. Removed `true` and `false` from the reserved keyword list — they are lexer-level reserved words that produce a literal token, not grammar-level keywords. Rationale: consistency with all other literal types (Sebesta §3.3.2 — tokens are classified by the lexer, grammar works with token classes).

  **Two notes from the audit (no decision required, already documented):**

  - The grammar is **LL(2) at one point**, not strictly LL(1): `<assign_stmt>` and `<call_stmt>` both start with `IDENT`, and the parser disambiguates by peeking the next token (`<-` → assignment, `(` → call). Standard recursive-descent handling. Must be documented in D1.
  - **Enum member names can collide with process attribute names** (e.g. `enum State { burst, ready }`). The grammar is unambiguous (enum members appear as bare `IDENT`; process attributes only after `.`), but the type checker must reject this collision per the §5.10 rule that member names live in the global namespace.

---

## PART 2 — Round 8 decisions (locked 20 May 2026)

### Round 8 — Type system, semantics, and runtime behavior (Part 2)

- [x] **5.27 Parameter-passing mode** — **Pass by value for primitives; always pass by reference for `semaphore` and `process`.**
  - Primitives (`int`, `float`, `bool`, `string`, `enum`) — callee gets a copy; caller's variable is never affected.
  - `semaphore` and `process` — always by reference. They are shared OS resources; reference semantics are the only meaningful mode.
  - No user-facing syntax for this — determined entirely by the type. No `&` symbol, no keyword.

  Exam justification: Sebesta §8.3.1 — pass-by-value protects caller data, strong reliability default. §8.3 — when the parameter is a shared resource by domain definition (semaphore, process), reference semantics are not a choice but a requirement.

- [x] **5.28 Short-circuit evaluation** — **Yes — both `&&` and `||` are short-circuit.**
  - `&&` — if left operand is `false`, right operand is **not evaluated**. Result is `false`.
  - `||` — if left operand is `true`, right operand is **not evaluated**. Result is `true`.
  - Both operands are only evaluated when the left operand does not determine the result.

  Exam justification: Sebesta §7.6 — short-circuit evaluation avoids unnecessary computation. If the result is already determined by the left operand, evaluating the right operand wastes resources and may cause unintended side effects.

- [x] **5.29 Operand evaluation order** — **Left to right.**
  - For expressions involving only primitive types this order has no observable effect — primitives are pass-by-value, no side effects possible.
  - The order matters when operands involve `semaphore` or `process` — because they are pass-by-reference and `wait`/`post` calls have real side effects on shared state.
  - Left-to-right is consistent with our left-associativity decision (§5.13) — same mental model, no surprises.

  Exam justification: Sebesta §7.5 — when operands have side effects (as semaphore operations do), evaluation order must be defined to guarantee deterministic behavior. Left-to-right is consistent with Java's guarantee of the same.

- [x] **5.30 Default value for `priority` when omitted** — **`0` (lowest priority).**
  - If `priority` is not specified in a process declaration, it defaults to `0`.
  - Higher integer = higher priority (e.g. `priority: 5` beats `priority: 2`).
  - A process with no declared priority is the least important in the system.

  Exam justification: Sebesta §5.4.3 — implicit default values must have a clear, domain-consistent meaning. Unspecified priority naturally means "not important" in OS scheduling. Improves writability (§1.3.2) — the user does not have to write `priority: 0` every time for a background process.

- [x] **5.31 Stop condition for `run` without `until`** — **Runs until all processes finished or deadlock detected.**
  - No artificial tick cap. The interpreter checks after each tick — if all processes are in `finished` state, simulation stops naturally.
  - If all processes are blocked and none can unblock (deadlock), simulation stops and prints a deadlock message.

  ```
  run(Sys1);          // stops when all processes finish, or deadlock detected
  run(Sys1, until: 20);   // stops at tick 20 regardless
  ```

  Exam justification: Sebesta §3.5 — semantics should reflect the actual meaning of the construct. The natural meaning of "run until done" is done when finished, not done when a calculated number is reached. Improves reliability (§1.3.3) — no silent early termination that confuses the programmer.

- [x] **5.32 `print` output format for enum** — **Prints the member name as a string, not the integer value.**
  - If the programmer wants the integer, they explicitly assign to an `int` variable first and print that.

  ```
  enum State { ready, running, blocked, finished }
  State s <- running;
  print(s);       // prints:  running
  int x <- s;
  print(x);       // prints:  1
  ```

  Exam justification: Sebesta §1.3.1 readability — output should be meaningful to the reader without requiring them to know the internal integer mapping. The integer backing is an implementation convenience for the programmer (writability §1.3.2), not information that belongs in the output.

- [x] **5.33 `add` behavior — creates an independent process instance.**
  - `system Sys1(processes: [P1], scheduler: FCFS)` → one instance of P1, arrives at its declared `arrival` time.
  - `Sys1.add(P1, arrival: 2)` → a second **independent instance** of P1, arrives at tick 2.
  - Both instances run independently — each has its own local variables (stack-dynamic).
  - Static variables are shared between all instances (static lifetime).

  ```
  process P1(burst: 3, arrival: 5) { }
  system Sys1(processes: [P1], scheduler: FCFS);
  Sys1.add(P1, arrival: 2);
  // Result: two P1 instances — one starts at tick 2, one at tick 5
  ```

  Exam justification: Sebesta §5.4.3 — stack-dynamic local variables mean each process activation has its own binding environment. Two instances of the same process are two separate activations — independent by definition. Matches the OS concept of a process template (program) vs a process instance.

- [x] **5.34 Strong typing rule — widening only, two implicit coercions.**
  - OSlang is strongly typed with exactly two implicit coercions, both widening only:

  | Coercion | Example | Safe? |
  |---|---|---|
  | `int` → `float` | `float x <- 5;` | ✅ widening, no data loss |
  | `enum` → `int` | `int x <- ready;` | ✅ widening, always safe |

  - Everything else is a compile-time type error:

  | Attempted coercion | Result |
  |---|---|
  | `float` → `int` | ❌ narrowing — compile-time error |
  | `int` → `bool` | ❌ compile-time error |
  | `string` → anything | ❌ compile-time error |
  | `enum A` → `enum B` | ❌ compile-time error (name equivalence) |
  | `int` literal in range → `enum` | ✅ allowed (§5.10) |
  | `int` expression → `enum` | ✅ compiles, runtime check |

  Exam justification: Sebesta §6.12 — strong typing means every type error is detected either at compile time or runtime, never silently ignored. §6.13 — implicit coercion weakens strong typing; we limit it to widening conversions only where data loss is impossible. Narrowing (`float` → `int`) is rejected at compile time because it silently discards the fractional part — a reliability violation (§1.3.3).

- [x] **5.35 Type equivalence (complete rule).**
  - **Enums** — name equivalence. `enum A` and `enum B` are different types even if they have the same members.
  - **Primitives** (`int`, `float`, `bool`, `string`) — name equivalence. `int` is only compatible with `int` (plus the two widening coercions from §5.34).
  - **`semaphore`** — its own type. Cannot be assigned to any other type. Exception: can be compared with `int` or another `semaphore` using `==`, `!=`, `<`, `>`, `<=`, `>=`.
  - **`process`** — only compatible with `process`. Cannot be assigned to or compared with any other type.

  ```
  if (mutex == 0) { ... }         // ✅ semaphore vs int
  if (mutex > empty) { ... }      // ✅ semaphore vs semaphore
  if (mutex == true) { ... }      // ❌ type error
  print(mutex);                   // ✅ prints current counter value as int
  ```

  Exam justification: Sebesta §6.15 — name equivalence is stricter than structural equivalence but eliminates a whole class of subtle bugs. A semaphore counter is an integer by definition, so comparing two semaphore counters is semantically meaningful — a controlled, domain-justified exception to strict name equivalence.

- [x] **5.36 Operational semantics constructs** — **`while` loop and `wait(s)`.**
  - Formal operational semantics for these two constructs will be written in D1 §4.4.
  - `while` — classic construct, well-known operational form, expected by examiner.
  - `wait(s)` — domain-specific, non-trivial state transition with two outcomes (block or continue).

  Exam justification: Sebesta §3.5 — operational semantics describes meaning by showing how a construct changes the state of an abstract machine. These two constructs represent the general-purpose (while) and domain-specific (wait) pillars of OSlang.

---

## PART 2 — Still deferred

- Design rationale paragraphs (D1 §4.8) — to be written in D1 document
- Detailed scheduler attributes (RR quantum behavior, etc.) — to be decided during interpreter implementation
- Operational semantics formal write-up (D1 §4.4) — to be written in D1 document

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
- [x] Round 6 — EBNF grammar fully locked (Steps 1–6 complete, all 4 review issues resolved)
- [x] Round 7 — Lexer implementation decisions locked (§5.25, §5.26)
- [x] Lexer implemented (TokenType.java, Token.java, Lexer.java, Main.java — Partner A)
- [x] Parser implemented (Parser.java, AST.java — Partner B)
- [x] D1 prose drafted
- [x] D3 example programs written (3 valid + 5 malformed)
- [x] D4 AI journal — 7 entries logged
- [x] D5 retrospective written
- [x] D6 contribution report written
- [x] Part 1 submitted on Teams

**Part 2 (due 22 May)**

- [x] Round 8 — Part 2 design decisions locked (§5.27–§5.36)
- [ ] D1 updated — §4.4 operational semantics written
- [ ] D1 updated — §4.5 type system written
- [ ] D1 updated — §4.6 expressions written
- [ ] D1 updated — §4.8 design rationale written
- [ ] Type checker implemented
- [ ] Interpreter implemented
- [ ] D3 updated — new test programs for type errors and interpreter output
- [ ] D4 AI journal — Part 2 entries logged
- [ ] D5 retrospective written
- [ ] Part 2 submitted on Teams

---

## 8. Build and Run

**Requirements:** Java 17 or later.

**Compile:**
```
javac -d out src/TokenType.java src/Token.java src/Lexer.java src/AST.java src/Parser.java src/Main.java
```

**Run — check for errors only:**
```
java -cp out Main program.osl
```

**Run — dump token stream:**
```
java -cp out Main program.osl --dump-tokens
```

**Run — dump AST:**
```
java -cp out Main program.osl --dump-ast
```

---

*Last updated: 20 May 2026.*