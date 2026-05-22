# OSlang — Design Document & Decisions (Part 1 + Part 2)

## 1. Domain

**OS simulator DSL.** OSlang lets a programmer declare processes, semaphores, and a scheduler, then run a deterministic tick-by-tick simulation that prints a human-readable trace.

The justification for a DSL here is strong: OS abstractions — ready queues, semaphore wait lists, context switches — are invisible at runtime in real systems. Textbooks teach them with prose and hand-drawn diagrams. OSlang makes these constructs first-class syntax.

The DSL surface makes a producer-consumer setup readable in ten lines instead of fifty.

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

- [x] **5.5 Identifier rules** — Letters, digits, underscore. Must start with a letter or underscore. Case-sensitive. No length limit enforced by the lexer.

  Sebesta §5.2 — identifiers should be unlimited length and case-sensitive for readability and writability.

- [x] **5.6 Reserved words vs keywords** — All special names in OSlang are reserved. Users cannot redefine `int`, `process`, `wait`, `post`, `print`, or any other language keyword.

  Sebesta §5.2 — reserving all keywords avoids ambiguity between user-defined and language-defined names.

- [x] **5.7 Scope rule** — **Static (lexical) scoping.**

  Sebesta §5.5.1 — static scoping allows the type and binding of every variable to be determined by reading the source, prior to execution.

- [x] **5.8 Scope levels** — Two levels only: global (top-level declarations) and local (inside a function or process body).

  No nested functions, no closures. Keeps the scope chain trivially implementable: a single global map plus one pushed local map per call.

- [x] **5.9 Variable lifetime** — Four cases:

  | Variable type | Lifetime | Example |
  |---|---|---|
  | `static` variable | Static — entire program execution | `static int counter <- 0;` |
  | Semaphore declaration | Static (implicit) | `semaphore mutex <- 1;` |
  | Process body local | Stack-dynamic — one activation per scheduled execution | `int temp <- 0;` inside process |
  | Function body local | Stack-dynamic — one activation per call | `int x <- 0;` inside func |

  Sebesta §5.4.3.1 — static variables are bound before execution and persist for its duration. §5.4.3.2 — stack-dynamic variables are allocated when the subprogram is called and deallocated on return.

- [x] **5.10 Mandatory initializers** — Every variable declaration must include an initializer. `int x;` is a syntax error.

  Sebesta §5.4.1 — uninitialized variables are a common reliability problem. Mandatory initializers eliminate the entire class.

---

### Round 3 — Type system (Sebesta Ch. 6)

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
  - `<-` matches the assignment operator decision (§5.4) — one binding form throughout.

- [x] **5.17 System declaration syntax** — **`system Name(processes: [...], scheduler: TYPE);`**
  - Process list is a bracketed comma-separated list of process names.
  - Scheduler is one of: `FCFS`, `SJF`, `SRTF`, `PRIORITY`, `RR(quant: N)`.
  - Only `RR` takes a parameter. All others are bare identifiers.

- [x] **5.18 Run statement syntax** — **`run(SystemName);`** or **`run(SystemName, until: N);`**
  - `until: N` runs for exactly N ticks.
  - Without `until`, the simulation runs until all processes finish or a tick limit is hit.

- [x] **5.19 `add` statement syntax** — **`SystemName.add(ProcessName, arrival: N);`**
  - Injects a second independent instance of a process into a running system.
  - `arrival` is mandatory on `add` — unlike the declaration default.

- [x] **5.20 `wait` / `post` as built-in calls** — **`wait(semName);`** and **`post(semName);`**
  - Not user-definable functions. Reserved at the lexer level.
  - `post` chosen over `signal` — `signal` is overloaded in the OS domain (UNIX signal handlers). `post` is unambiguous.

- [x] **5.21 `print` as a built-in call** — **`print(expr);`**
  - Accepts any type. One argument.
  - Reserved at the lexer level — users cannot define a function named `print`.

- [x] **5.22 `func` declaration syntax** — **`func Name(param: type, ...) -> returnType { body }`**
  - Named parameters with explicit types.
  - Return type mandatory — no void functions.
  - `return expr;` mandatory — bare `return;` is a syntax error.

- [x] **5.23 Control flow** — `if / elif / else` and `while`. Conditions always parenthesized. Bodies always braced blocks.
  - `elif` is a single keyword token, not `else if`.
  - No `for`, no `do-while`, no `break`, no `continue`.

- [x] **5.24 Enum declaration syntax** — **`enum Name { member, member, ... }`**
  - Members assigned integer ordinals 0, 1, 2, … implicitly.
  - The enum name and member names follow the standard identifier rules (§5.5) and are case-sensitive.
  - Enum declarations are top-level only.
  - Member names live in the global namespace — collisions with other identifiers are a compile-time error.

  **Two-layer range enforcement for int-to-enum assignments (Sebesta §6.13, §6.14):**

  | Right-hand side | When checked | Rule |
  |---|---|---|
  | Enum member | — | Always valid |
  | Enum variable of the same type | — | Always valid |
  | `INT_LIT` | **Compile time** | Rejected if literal ∉ `[0, memberCount-1]` |
  | Integer expression or variable | **Runtime** | Error thrown if value ∉ `[0, memberCount-1]` |

  Sebesta §6.4.1 identifies range validity as a core enum design issue. §6.13 — type errors must be detected statically or dynamically. §6.14 — coercion weakens strong typing; we limit int-to-enum to provably valid cases. §6.15 — name equivalence prevents cross-enum assignment bugs.

---

### Round 6 — EBNF grammar (Sebesta §3.3)

- [x] **5.25 Grammar decisions during grammar writing:**
  - `wait`, `post`, `print` are reserved keywords at the lexer level.
  - `elif` is a single keyword token.
  - `true` / `false` emit `BOOL_LIT` tokens, not keyword tokens.
  - The grammar is **LL(2) at one point** — `<assign_stmt>` and `<call_stmt>` both start with `IDENT`, disambiguated by peeking the second token.
  - Enum member names can collide with process attribute names — type checker must reject this.

- [x] **5.26 Lexer implementation decisions:**
  - Maximal munch for multi-character tokens: `<-`, `<=`, `>=`, `==`, `!=`, `&&`, `||`.
  - Two distinct priority keyword tokens: `KW_PRIORITY` (scheduler context) and `KW_PRIORITY_F` (process field context).
  - String literals unescaped at lex time.
  - Removed `true` and `false` from the reserved keyword list — they produce a literal token, not a grammar-level keyword.

---

## PART 2 — Decisions required by 22 May

### Round 8 — Semantics and expressions (Sebesta Ch. 3, 7)

- [x] **5.27 Parameter-passing mode** — **Pass by value for primitives; always pass by reference for `semaphore` and `process`.**
  - Primitives (`int`, `float`, `bool`, `string`, `enum`) — callee gets a copy; caller's variable is never affected.
  - `semaphore` and `process` — always by reference. A pass-by-value copy would be thrown away on return, meaning any `wait` or `post` inside the function would have no effect on the original semaphore — which would silently break synchronization.
  - No user-facing syntax — determined entirely by the type. No `&` symbol, no keyword.

  Sebesta §9.5.2.1 — pass-by-value gives the callee a local copy; fast for scalars and protects the caller's data. §9.5.2.3 — pass-by-reference transmits an access path to the caller's actual variable; required here so that mutations via `wait`/`post` are visible after the function returns.

- [x] **5.28 Short-circuit evaluation** — **Yes — both `&&` and `||` short-circuit.**
  - `&&` — if left operand is `false`, right operand is not evaluated. Result is `false`.
  - `||` — if left operand is `true`, right operand is not evaluated. Result is `true`.

  Sebesta §7.6 — short-circuit avoids unnecessary computation and prevents potential runtime errors from the unevaluated operand. The §7.6 warning about side effects in skipped operands does not apply — OSlang has no side effects in expressions (no `++`, no assignment-as-expression).

- [x] **5.29 Operand evaluation order** — **Left to right, always.**
  - For primitive-only expressions this has no observable effect — pass-by-value means no side effects.
  - Order matters when operands involve `semaphore` or `process` — `wait`/`post` have real side effects on shared state.
  - Consistent with left-associativity decision (§5.13) — same mental model throughout.

  Sebesta §7.5 — when operands have side effects, evaluation order must be defined to guarantee deterministic behavior.

- [x] **5.36 Operational semantics constructs** — **`while` loop and `wait(s)`.**
  - `while` — classic construct, well-known form, expected by examiner.
  - `wait(s)` — domain-specific, non-trivial: two possible outcomes (block or continue).
  - Formal write-up goes in D1 §4.4.

  Sebesta §3.5 — operational semantics describes meaning by showing how a construct changes the state of an abstract machine.

---

### Round 9 — Type system (Sebesta Ch. 6)

- [x] **5.30 Default value for `priority` when omitted** — **`0` (lowest priority).**
  - Higher integer = higher priority (e.g. `priority: 5` beats `priority: 2`).
  - A process with no declared priority is the least important in the system.

  Sebesta §5.4.3 — implicit defaults must have a clear, domain-consistent meaning. Improves writability (§1.3.2) — no need to write `priority: 0` every time for a background process.

- [x] **5.31 Strong typing rule — widening only, two implicit coercions.**
  - OSlang is **nearly strongly typed** (Sebesta §6.14). Exactly two implicit coercions allowed, both widening only:

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

  "Nearly strongly typed" because Sebesta §6.14 defines a strongly typed language as one where *all* type errors are detected. The two widening coercions are intentional exceptions — widening never loses data, but they do reduce the theoretical error-detection benefit. This is the same trade-off Java makes; Sebesta §6.14 uses Java as the canonical example of this model.

  Sebesta §6.13 — narrowing coercion silently discards data — a reliability violation (§1.3.3). §6.14 — coercion weakens strong typing; our two widening-only coercions are deliberate and safe.

- [x] **5.32 Type equivalence — name equivalence for all types.**
  - **Primitives** — name equivalence. `int` only compatible with `int` (plus widening coercions above).
  - **Enums** — name equivalence. `enum A` and `enum B` are different types even with identical members.
  - **`semaphore`** — its own type. Cannot be assigned to any other type. Exception: can be compared with `int` or another `semaphore` using `==`, `!=`, `<`, `>`, `<=`, `>=`.
  - **`process`** — only compatible with `process`. Cannot be compared with any other type.

  ```
  if (mutex == 0) { ... }      // ✅ semaphore vs int
  if (mutex > empty) { ... }   // ✅ semaphore vs semaphore
  if (mutex == true) { ... }   // ❌ type error
  print(mutex);                // ✅ prints current counter value as int
  ```

  Sebesta §6.15 — name equivalence eliminates subtle bugs where two types happen to look alike but mean different things.

- [x] **5.33 `print` output format for enum** — **Prints the member name string, not the integer ordinal.**
  - The ordinal is still used internally for comparisons and int-to-enum coercion — never shown via `print`.

  ```
  enum State { ready, running, blocked, finished }
  State s <- running;
  print(s);      // prints:  running
  int x <- s;
  print(x);      // prints:  1
  ```

  Sebesta §1.3.1 readability — output must be meaningful without knowing the internal integer mapping. §6.4.2 — Java's enum toString() returns the member name for the same reason.

---

### Round 10 — Runtime and simulation behavior (Sebesta Ch. 3, 5)

- [x] **5.34 Stop condition for `run` without `until`** — **Runs until all processes finished or deadlock detected.**
  - All processes `FINISHED` → simulation ends naturally.
  - All processes `BLOCKED` and none can unblock → deadlock message printed, simulation ends.

  Sebesta §3.5 — semantics should reflect actual meaning. "Run until done" means done when finished. Improves reliability (§1.3.3) — no silent early termination.

- [x] **5.35 `add` behavior — creates an independent process instance.**
  - `system Sys1(processes: [P1], scheduler: FCFS)` → one instance of P1, arrives at declared `arrival` time.
  - `Sys1.add(P1, arrival: 2)` → a second independent instance of P1, arrives at tick 2.
  - Each instance has its own local variables (stack-dynamic). Static variables are shared.
  - Display name in trace: first instance is `P1`, second is `P1#1`.

  Sebesta §5.4.3.2 — stack-dynamic local variables mean each activation has its own binding environment. Two instances of the same process are two separate activations.

- [x] **5.36 RR quantum behavior — real Round Robin, no wasted ticks.**
  - Process that **finishes mid-quantum** → releases CPU immediately. No wasted ticks.
  - Process that **exhausts its quantum** without finishing → moved to back of ready queue.
  - At start of each tick: arrivals are processed first, then scheduler picks from front of queue.
  - Newly arrived process joins back of queue — cannot jump ahead of processes already waiting.

  Sebesta §3.5.1 — the tick-step rules in D1 §4.4 are written as informal operational semantics: each rule shows how one construct changes the simulation state. Real RR never holds the CPU for a finished process; bounded waiting guarantees every process gets a turn within `(n−1) × quantum` ticks.

---

### Round 11 — Type checker architecture (Sebesta Ch. 6)

- [x] **5.37 Compile-time type representation — single `OSlangType` class with a tag + optional enum fields.**
  - One class with a `Kind` tag (`INT`, `FLOAT`, `BOOL`, `STRING`, `SEMAPHORE`, `PROCESS`, `VOID`, `ENUM`) plus two extra fields, `enumName` and `memberCount`, meaningful only when kind is `ENUM`.
  - `enumName` exists because §5.32 type equivalence is name equivalence.
  - `memberCount` exists because the §5.10 range check needs to know `[0, memberCount-1]`.
  - `equals()` compares kinds; for `ENUM` also compares `enumName`. `memberCount` is NOT part of equality.

  Sebesta §6.1 — a compile-time type is a distinct notion from a runtime value.

- [x] **5.38 Dispatch shape — `check()` for statements/declarations, `checkExpr()` for expressions.**
  - `checkExpr(node)` returns an `OSlangType`; `check(node)` returns nothing.
  - Mirrors the interpreter's `evaluate` / `execute` pair and matches the work split.

  Sebesta §6.13 — separating expression typing from statement checking keeps responsibility explicit.

- [x] **5.39 Symbol table — flat global map + pushed scope inside function/process bodies.**
  - All top-level names live in a single global scope.
  - A nested scope is pushed only when entering a function or process body and popped on exit.

  Sebesta §5.5 — static scoping resolves names by lexical structure; OSlang's two-level structure makes a flat global plus one pushed local scope sufficient.

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

- [x] Round 8 — Semantics and expressions locked (§5.27–§5.29, §5.36)
- [x] Round 9 — Type system locked (§5.30–§5.33)
- [x] Round 10 — Runtime and simulation behavior locked (§5.34–§5.36)
- [x] Round 11 — Type checker architecture locked (§5.37–§5.39)
- [x] `RuntimeValue.java` — shared value structure created
- [x] `Environment.java` — scope chain created
- [ ] `TypeChecker.java` — skeleton created
- [ ] `Interpreter.java` — skeleton created
- [ ] `Main.java` — updated to call type checker then interpreter
- [ ] D1 updated — §4.4 operational semantics (`while` + `wait`)
- [ ] D1 updated — §4.5 type system
- [ ] D1 updated — §4.7 expressions (precedence, associativity, short-circuit)
- [ ] D1 updated — §4.8 design rationale
- [ ] Type checker implemented (expression checker + declaration checker)
- [ ] Interpreter implemented (statement executor + simulation engine)
- [ ] D3 updated — new test programs for type errors and interpreter output
- [ ] D4 AI journal — Part 2 entries logged (min 6 new entries)
- [ ] D5 retrospective written
- [ ] D6 contribution report updated
- [ ] Part 2 submitted on Teams

---

## 8. Build and Run

**Requirements:** Java 17 or later.

**Compile (Part 1):**
```
javac -d out TokenType.java Token.java Lexer.java AST.java Parser.java Main.java
```

**Compile (Part 2 — includes type checker and interpreter):**
```
javac -d out TokenType.java Token.java Lexer.java AST.java Parser.java RuntimeValue.java Environment.java TypeChecker.java Interpreter.java Main.java
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

*Last updated: 22 May 2026.*