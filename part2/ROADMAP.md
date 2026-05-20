# OSlang — Part 2 Roadmap

**Deadline:** Friday, 22 May 2026 · 23:59
**Exam:** Thursday, 28 May 2026 · 08:30

> This file is the living roadmap for Part 2. Check off tasks as they are done. Both partners must read and understand every section — the exam asks questions about components you did not personally write.

---

## Locked decision — made today

**`print(enumVar)` prints the member name string, not the integer ordinal.**

```
enum Day { Monday, Tuesday, Wednesday }
Day d <- Monday;
print(d);        // prints:  Monday   (not 0)
print(Tuesday);  // prints:  Tuesday  (not 1)
```

Document this in D1 §4.5 and implement it in the interpreter's `print` handler. The integer ordinal is still used internally for comparisons and int-to-enum coercion — it is just never shown to the user via `print`.

---

## What Part 2 adds on top of Part 1

| Deliverable | What must be added |
|---|---|
| D1 | §4.4 Semantics, §4.5 Type System, §4.7 Expressions section, §4.8 Design rationale |
| D2 | `TypeChecker.java`, `Interpreter.java`, updated `Main.java` |
| D3 | More test programs showing type errors and interpreter trace output |
| D4 | Individual AI journal — new dated entries for Part 2 work |
| D5 | Individual retrospective for Part 2 |
| D6 | Updated contribution report covering Part 2 split |

All Part 1 deliverables may be revised. If the grader gave feedback, fix it now.

---

## Remaining design decisions to lock (Phase 1 — do together)

These were explicitly deferred in the README. Lock them before writing any code.

- [ ] **Default value for `priority` when omitted** — suggested: `0` (lowest priority, consistent with FCFS treating all equally). Document in D1 §4.3 process field table.
- [ ] **Max tick limit for `run` without `until`** — suggested: `1000`. Prevents infinite loops on deadlocked programs. The trace ends with a timeout message if hit.
- [ ] **Short-circuit evaluation for `&&` and `||`** — decision: yes, short-circuit (left operand determines result when possible). Document in D1 §4.7.
- [ ] **Operand evaluation order** — decision: left-to-right, always. Document in D1 §4.7.
- [ ] **Parameter passing mode** — decision: call-by-value for `int`, `float`, `bool`, `string`; call-by-reference for `semaphore` and `process` (they are handles, copying them would be meaningless). Document in D1 §4.5.
- [ ] **RR quantum behavior** — decision: a process that finishes mid-quantum does not hold the CPU for the remainder. The next process starts on the next tick. A process that exhausts its quantum is moved to the back of the ready queue. Document in D1 §4.4.

---

## Phase 1 — Lock decisions + skeleton setup
**Who:** Both together · **When:** Day 1 (today)

- [ ] Agree on all six deferred decisions above and write them into the README
- [ ] Add them to D1 (in the relevant sections)
- [ ] Create skeleton files: `TypeChecker.java`, `Interpreter.java`, `Environment.java`, `RuntimeValue.java`
- [ ] Agree on `Environment` API: `define(name, value)`, `assign(name, value)`, `lookup(name)`, `push()`, `pop()` — this must be consistent before splitting work
- [ ] Agree on `RuntimeValue` structure: what Java type holds an `int`, a `float`, a `bool`, a `string`, a `semaphore` (counter + wait queue), an enum value (name + ordinal), a process handle
- [ ] Update `Main.java` to call the type checker and then the interpreter after the parser

---

## Phase 2 — D1 additions
**When:** Day 1–2 · Cross-review at the end before moving to code

### Ferhat writes

- [ ] **D1 §4.4 Semantics — `while` loop** (Sebesta §3.5, small-step operational rules)
  - Write the transition rules: what state changes on each step of `while (cond) { body }`
  - Show: condition evaluates to false → loop exits; condition evaluates to true → body executes, then loop repeats
  - Use inference-rule notation or a numbered small-step table — pick one style and stick to it
- [ ] **D1 §4.7 Expressions section** (Sebesta Ch. 7)
  - Precedence table (all 7 levels from the EBNF)
  - Associativity rule (all binary operators left-associative; unary right-associative) with examples
  - Short-circuit evaluation: `&&` stops at first false, `||` stops at first true — give a code example
  - Operand evaluation order: left-to-right always
  - Mixed-mode arithmetic: `int + float → float` (widening), `float` assigned to `int` → compile error
- [ ] **D1 §4.5 Type System — enum rules** (Sebesta §6.4, §6.13–6.15)
  - Enum type rules: int-to-enum coercion (compile-time check for literals, runtime check for expressions)
  - `print(enumVar)` prints the member name string

### Tuana writes

- [ ] **D1 §4.4 Semantics — process execution / wait / post** (Sebesta §3.5, tick-step rules)
  - Write the transition rules for one simulation tick: scheduler picks a process → process executes one step → semaphore state may change → blocked/unblocked transitions
  - Show `wait(s)`: if `s > 0` decrement and continue; else block the process
  - Show `post(s)`: if wait queue non-empty unblock one process; else increment `s`
- [ ] **D1 §4.5 Type System — main section** (Sebesta Ch. 6)
  - Strong typing declaration: OSlang is strongly typed (no implicit narrowing, no type punning)
  - Coercion table: which conversions are allowed, which are compile-time errors
  - Type equivalence: name equivalence for enums, structural equivalence not used
  - Parameter passing: call-by-value for primitives, call-by-reference for semaphore/process
- [ ] **D1 §4.8 Design rationale**
  - One paragraph per major design decision: `<-` operator, static scoping, no heap, `elif` keyword, mandatory initializers, `post` over `signal`, fixed field order in `system`
  - Each paragraph must cite the relevant Sebesta section

### Both together

- [ ] Cross-read each other's D1 sections — you must understand what the other wrote for the exam
- [ ] Check that §4.4 semantics and §4.5 type system are consistent with each other and with the code

---

## Phase 3 — Type Checker
**When:** Day 2–3

### Both together first

- [ ] Design `TypeChecker.java` skeleton: one `check(ASTNode)` method that dispatches on node type
- [ ] Define `OSlangType` enum or class: `INT`, `FLOAT`, `BOOL`, `STRING`, `SEMAPHORE`, `PROCESS`, `VOID`, `ENUM(name)`
- [ ] Define the symbol table structure: global scope holds all top-level names; function/process scopes are pushed/popped

### Ferhat implements

- [ ] **Expression type checker** — returns the type of an expression, throws on mismatch
  - `BinOpNode`: arithmetic operators require numeric operands; `+` on `int+float` returns `float`; `==`/`!=` require matching types; `&&`/`||` require `bool` operands
  - `UnaryOpNode`: `!` requires `bool`; unary `-` requires `int` or `float`
  - `FuncCallNode`: look up function signature, check arity, check each argument type
  - `PostfixDotNode`: object must be `process` type; attribute name determines result type (`state` → enum, `burst`/`priority`/`arrival` → `int`)
  - `IdentNode`: look up in scope chain, return its declared type
  - All literal nodes: return their obvious type
- [ ] **Statement checker — inside blocks**
  - `AssignStmtNode`: right-hand side type must be compatible with declared type of variable (no narrowing)
  - `VarDeclStmtNode`: initializer type must match declared type
  - `ReturnStmtNode`: expression type must match the enclosing function's declared return type
  - `IfStmtNode` / `WhileStmtNode`: condition must be `bool`

### Tuana implements

- [ ] **Declaration checker — top-level**
  - `StaticDeclNode`: check declared type exists (enum names must be declared), check initializer type
  - `SemaphoreDeclNode`: initializer must be `int`, value must be ≥ 0 (literal check at compile time; expression check deferred to runtime)
  - `EnumDeclNode`: no duplicate member names, no collision with other global names
  - `ProcessDeclNode`: `burst` field mandatory; no duplicate fields; all field values must be `int` and > 0
  - `FuncDeclNode`: parameter names unique, return type is a valid simple type
- [ ] **Domain-specific checks**
  - `CallStmtNode` for `wait`/`post`: single argument, argument must be `semaphore` type
  - `CallStmtNode` for `print`: single argument, any type allowed
  - `SystemDeclNode`: all process names in the list must refer to declared processes; `quant` for RR must be > 0
  - `RunStmtNode`: system name must refer to a declared system; `until` value must be `int`
  - `AddStmtNode`: system name must refer to a declared system; process name must refer to a declared process

### Both together

- [ ] Integrate Ferhat's and Tuana's type checker halves
- [ ] Run all 8 existing test programs — the 3 valid ones must still pass, the 5 malformed ones must now produce type errors (or still produce the same parse errors as before)
- [ ] Write 2–3 new programs that specifically trigger type errors (wrong type in assignment, wrong arg to `wait`, semaphore initial value negative)

---

## Phase 4 — Interpreter
**When:** Day 3–4

### Both together first

- [ ] Design `Interpreter.java` skeleton: `execute(ASTNode)` for statements, `evaluate(ASTNode)` for expressions
- [ ] Agree on `Environment` at runtime: a linked list of scopes; each scope is a `Map<String, RuntimeValue>`
- [ ] Agree on `RuntimeValue`: a tagged union — holds one of: `int`, `double`, `boolean`, `String`, `SemaphoreValue` (counter + waitQueue), `EnumValue` (typeName + memberName + ordinal), `ProcessHandle`
- [ ] Agree on how `return` propagates: use a `ReturnException` (a Java exception used as a control-flow signal — clean and simple)
- [ ] Agree on the trace output format — what each line of the simulation output looks like, e.g.:
  ```
  [tick  1] RUNNING: Producer
  [tick  2] RUNNING: Producer  |  wait(mutex): blocked
  [tick  3] RUNNING: Consumer
  ```

### Tuana implements

- [ ] **Expression evaluator** — `evaluate(ASTNode) → RuntimeValue`
  - All literal nodes: wrap value in `RuntimeValue`
  - `IdentNode`: look up in environment
  - `BinOpNode`: evaluate both sides, apply operator, handle int/float widening
  - `UnaryOpNode`: evaluate operand, apply `!` or unary `-`
  - `FuncCallNode`: push new scope, bind arguments, execute body, catch `ReturnException`, pop scope
  - `PostfixDotNode`: evaluate object, read the named attribute from the process handle

### Ferhat implements

- [ ] **Statement executor** — `execute(ASTNode)`
  - `VarDeclStmtNode`: evaluate initializer, define in current scope
  - `AssignStmtNode`: evaluate expression, update existing binding in scope chain
  - `IfStmtNode` / `WhileStmtNode`: evaluate condition, execute blocks, short-circuit `&&`/`||`
  - `ReturnStmtNode`: evaluate expression, throw `ReturnException`
  - `CallStmtNode` for `print`: evaluate argument, format output (enum → member name string)
  - `BlockNode`: push scope, execute all statements, pop scope
- [ ] **Simulation engine core**
  - Tick loop: `for tick = 0; tick < limit; tick++`
  - Ready queue management: processes move between READY, RUNNING, BLOCKED, FINISHED
  - FCFS scheduler: always pick the process with the lowest arrival time that is ready
  - PRIORITY scheduler (non-preemptive): pick highest priority ready process; once running, it runs to completion
  - Print tick trace line at each tick

### Tuana implements

- [ ] **Semaphore runtime**
  - `wait(s)`: if `s.counter > 0` decrement and continue; else add process to `s.waitQueue` and mark BLOCKED
  - `post(s)`: if `s.waitQueue` non-empty pop one process and mark it READY; else increment `s.counter`
- [ ] **Remaining schedulers**
  - SJF: pick the ready process with the smallest `burst` value (non-preemptive)
  - SRTF: pick the ready process with the smallest remaining burst (preemptive — check at every tick)
  - RR: pick the next process in round-robin order; track remaining quantum per process; on quantum expiry move to back of queue
- [ ] **`add` statement runtime**
  - At the tick matching the `arrival` value, inject the process into the system's ready queue
  - Runtime error if `arrival` tick has already passed when `add` is called

### Both together

- [ ] Integrate all interpreter parts
- [ ] Run the full producer-consumer example (Program 1 from D3) end to end
- [ ] Verify the tick-by-tick trace is correct by hand-tracing the first 5 ticks
- [ ] Run all valid test programs and confirm they produce sensible output

---

## Phase 5 — Final tests + individual deliverables
**When:** Day 4–5

### Both together

- [ ] **D3 expanded test programs** — add at least 3 new programs:
  - One that shows a type error caught at compile time (wrong type, negative semaphore, etc.)
  - One that runs with SJF or SRTF scheduler and shows the trace
  - One that uses an enum variable, prints it, and shows the member name output
- [ ] Update D3 test report with the new programs and their expected output
- [ ] Final end-to-end check: compile all Java, run all programs, no crashes
- [ ] Update D6 contribution report to reflect Part 2 work split

### Ferhat — individual

- [ ] **D4 AI journal** — add new entries for Part 2 sessions (must be dated, must include what was accepted/rejected)
- [ ] **D5 retrospective** — write your own reflection on Part 2: what was harder than expected, what you would do differently

### Tuana — individual

- [ ] **D4 AI journal** — add new entries for Part 2 sessions (her own journal, separate from Ferhat's)
- [ ] **D5 retrospective** — her own reflection on Part 2

---

## Exam prep — both must do this

The exam is **28 May**. Questions will be targeted at your own submission but can cover any component.

- [ ] Ferhat: be able to explain Tuana's type checker declaration checks and her semaphore runtime code
- [ ] Tuana: be able to explain Ferhat's expression type checker and his simulation engine
- [ ] Both: be able to trace through the producer-consumer example by hand (first 5 ticks, on paper)
- [ ] Both: be able to write the operational semantics rules for `while` and `wait`/`post` from memory
- [ ] Both: be able to answer "why did you choose X over Y" for every decision in §4.8

---

## File checklist for submission

```
D1  OSlang_D1_P2.pdf          — complete revised design spec (all sections)
D2  source/
      TokenType.java
      Token.java
      Lexer.java
      AST.java
      Parser.java
      TypeChecker.java          ← new
      Interpreter.java          ← new
      Environment.java          ← new
      RuntimeValue.java         ← new
      Main.java                 ← updated
D3  OSlang_D3_P2.pdf          — expanded test report
D4  [each partner submits own] OSlang_D4_P2_Ferhat.pdf
                               OSlang_D4_P2_Tuana.pdf
D5  [each partner submits own] OSlang_D5_P2_Ferhat.pdf
                               OSlang_D5_P2_Tuana.pdf
D6  OSlang_D6_P2.pdf          — updated contribution report
```

---

*Last updated: 19 May 2026*