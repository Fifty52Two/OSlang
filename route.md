# OSlang — Part 2 Roadmap
**Deadline:** Friday, 22 May 2026 · 23:59
**Exam:** Thursday, 28 May 2026 · 08:30

> **Work split agreement:**
> - **Tuana** → TypeChecker (all declarations) + Expression Evaluator in Interpreter + D1 §4.4, §4.5, §4.8
> - **Ferhat** → Statement Executor + Simulation Engine in Interpreter + D1 §4.4 (`while`), §4.7
> - **Both** → All core decisions, skeleton design, integration, testing, individual D4/D5/D6

---

## STEP 0 — Lock deferred decisions (Both together · ~30 min)

These must be agreed before anyone writes a single line of code.
Write each decision into the README and into D1 immediately after agreeing.

- [ ] **Default `priority` when omitted** → `0` (Sebesta §1.3 — reliability, predictable defaults)
- [ ] **Max tick limit for `run` without `until`** → `1000` ticks, print timeout message if hit
- [ ] **Short-circuit `&&` / `||`** → yes, left operand first; right operand skipped when result is determined (Sebesta §7.6)
- [ ] **Operand evaluation order** → left-to-right always (Sebesta §7.2)
- [ ] **Parameter passing mode** → call-by-value for `int`, `float`, `bool`, `string`; call-by-reference for `semaphore` and `process` (Sebesta Ch. 9)
- [ ] **RR quantum behavior** → process finishing mid-quantum releases CPU immediately; quantum exhausted → back of ready queue (Sebesta §1.3 — writability)
- [ ] **RR quantum mid-block behavior** → process that calls `wait(s)` and blocks mid-quantum gets a fresh full quantum when it unblocks *(decide this now — do not leave it open)*

---

## STEP 1 — Agree on shared interfaces (Both together · ~20 min)

These are the contracts both partners depend on. Agree before splitting.

- [ ] **`Environment` API** — both partners call this from different components:
  - `define(String name, RuntimeValue val)` → creates binding in current scope
  - `assign(String name, RuntimeValue val)` → walks scope chain, updates existing binding
  - `lookup(String name)` → returns value or throws if undefined
  - `push()` → opens a new inner scope
  - `pop()` → closes the current inner scope (always in `finally`)

- [ ] **`OSlangType` enum** — shared by TypeChecker and Interpreter:
  - `INT`, `FLOAT`, `BOOL`, `STRING`, `SEMAPHORE`, `PROCESS`, `VOID`
  - `ENUM` carries the enum name — `ENUM("State")` ≠ `ENUM("Day")` (name equivalence, Sebesta §6.15)

- [ ] **`RuntimeValue` tagged union** — produced by Tuana's evaluator, consumed by Ferhat's executor:
  - `int` → Java `int`
  - `float` → Java `double`
  - `bool` → Java `boolean`
  - `string` → Java `String`
  - `semaphore` → `SemaphoreValue { int counter; Queue<ProcessHandle> waitQueue; }`
  - `enum value` → `EnumValue { String typeName; String memberName; int ordinal; }`
  - `process` → `ProcessHandle { String name; int burst; int remaining; int priority; int arrival; ProcessState state; }`

- [ ] **`ReturnException`** — Java exception used as a control-flow signal for `return` statements; caught only at function call boundary

- [ ] **Tick trace output format** — agree exactly what each line looks like, e.g.:
  ```
  [tick  1] RUNNING: Producer
  [tick  2] RUNNING: Producer  |  wait(mutex): BLOCKED
  [tick  3] RUNNING: Consumer
  ```

- [ ] **Create skeleton files** (empty classes with method stubs):
  - `TypeChecker.java`
  - `Interpreter.java`
  - `Environment.java`
  - `RuntimeValue.java`

- [ ] **Update `Main.java`** to call TypeChecker then Interpreter after the parser

---

## STEP 2 — D1 design document additions (Split · write in parallel)

Cross-review at the end of this step before moving to code.

### Tuana writes

- [ ] **D1 §4.4 Semantics — tick-step rules for simulation**
  - Rule for one simulation tick: scheduler picks READY process → process executes one step → semaphore state may change → BLOCKED/READY transitions update
  - `wait(s)` rule: if `s.counter > 0` → decrement and continue; else → add current process to `s.waitQueue`, mark process BLOCKED
  - `post(s)` rule: if `s.waitQueue` non-empty → pop one process, mark it READY; else → increment `s.counter`
  - Use a numbered small-step table OR inference-rule notation — pick one, be consistent (Sebesta §3.5)

- [ ] **D1 §4.5 Type System — main section**
  - Strong typing statement: OSlang is strongly typed — no implicit narrowing, no type punning (Sebesta §6.14)
  - Coercion table: `int → float` widening allowed; `float → int` is a compile-time error; no other implicit conversions
  - Type equivalence: name equivalence for enums — `State == State` valid, `State == Day` is a type error even if both are enums (Sebesta §6.15)
  - Parameter passing table: call-by-value for primitives, call-by-reference for `semaphore`/`process`
  - `print(enumVar)` → prints member name string, not integer ordinal (locked decision)

- [ ] **D1 §4.8 Design rationale** — one paragraph per decision, each citing Sebesta:
  - `<-` operator → Sebesta §7.7 (eliminates `=` vs `==` confusion, reliability)
  - Static scoping → Sebesta §5.5 (readability, predictable variable resolution)
  - No heap / no memory management → Sebesta §1.3 (reliability trade-off, scope control)
  - `elif` keyword → Sebesta §3.3.1 (dangling-else avoidance, readability)
  - Mandatory initializers → Sebesta §5.4.2, §5.4.3.2 (reliability, no undefined state)
  - `post` over `signal` → Sebesta §1.3 (writability, UNIX signal ambiguity in OS domain)
  - Fixed field order in `system` → Sebesta §1.3 (grammar enforces constraints, readability)

### Ferhat writes

- [ ] **D1 §4.4 Semantics — `while` loop transition rules**
  - Small-step rules: cond evaluates to false → loop exits; cond evaluates to true → body executes → repeat
  - Use the same notation style Tuana uses in her section

- [ ] **D1 §4.7 Expressions section**
  - Full 7-level precedence table from the EBNF
  - Associativity: all binary operators left-associative; unary right-associative; with code examples
  - Short-circuit: `&&` stops at first false, `||` stops at first true — give a code example
  - Operand evaluation order: left-to-right always
  - Mixed-mode arithmetic: `int + float → float`; `float` assigned to `int` → compile error
  - Enum coercion: int-to-enum allowed with compile-time check for literals

### Both together

- [ ] Cross-read each other's D1 sections
- [ ] Verify §4.4 semantics and §4.5 type system are consistent with each other
- [ ] Verify notation style is consistent across both §4.4 sections

---

## STEP 3 — TypeChecker implementation (Tuana)

Start with the `TypeChecker.java` skeleton from Step 1.
One `check(ASTNode)` method that dispatches on node type.
Symbol table: global scope holds all top-level names; function and process scopes are pushed/popped.

### Both together first
- [ ] Confirm `OSlangType` enum covers all needed types
- [ ] Confirm symbol table structure before Tuana starts

### Tuana implements — declaration checks

- [ ] **`ProcessDeclNode`**
  - `burst` field is mandatory → type error if missing
  - No duplicate field names (`burst`, `priority`, `arrival`)
  - Field values must be positive integers
  - Check process body block using a new scope

- [ ] **`SemaphoreDeclNode`**
  - Initial value must be a non-negative integer (Sebesta §6.2 — integer type constraints)

- [ ] **`FuncDeclNode`**
  - No duplicate parameter names
  - Push a new scope with parameters bound to their declared types
  - All `return` statements in the body must return the declared return type
  - Pop scope after checking body

- [ ] **`EnumDeclNode`**
  - No duplicate member names
  - Member names must not collide with any already-declared global identifier

- [ ] **`VarDeclStmtNode` / `StaticDeclNode`**
  - Evaluate initializer type
  - Initializer type must match declared type (or be a valid widening coercion)

- [ ] **`CallStmtNode` for `print`**
  - Exactly one argument; any type is allowed

- [ ] **`SystemDeclNode`**
  - All process names in the list must refer to already-declared processes
  - If scheduler is `RR`, `quant` value must be `> 0`

- [ ] **`RunStmtNode`**
  - System name must refer to a declared system
  - `until` value, if present, must be type `int`

- [ ] **`AddStmtNode`**
  - System name must refer to a declared system
  - Process name must refer to a declared process
  - `arrival` expression must be type `int`

### Ferhat implements — expression type checks

- [ ] `BinOpNode`, `UnaryOpNode`, `FuncCallNode`, `AssignStmtNode`, `IfStmtNode`/`WhileStmtNode` conditions, `ReturnStmtNode`

### Both together — integration

- [ ] Integrate both halves of the type checker
- [ ] Run the 3 valid test programs — they must still pass with no type errors
- [ ] Run the 5 malformed programs — must produce type errors or same parse errors as before
- [ ] Write 2–3 new programs that specifically trigger type errors:
  - Wrong type in assignment (e.g. `int x <- 3.14;`)
  - Wrong argument to `wait` (e.g. `wait(5);` — not a semaphore)
  - Negative semaphore initial value (e.g. `semaphore s <- -1;`)

---

## STEP 4 — Interpreter implementation (Split)

Start with the `Interpreter.java` skeleton from Step 1.
Two main methods: `evaluate(ASTNode) → RuntimeValue` for expressions, `execute(ASTNode)` for statements.

### Both together first
- [ ] Confirm `Environment` linked-list scope structure
- [ ] Confirm `RuntimeValue` tagged union is complete
- [ ] Confirm `ReturnException` propagation: thrown in `ReturnStmtNode`, caught only at `FuncCallNode`; scope pop always in `finally`
- [ ] Confirm tick trace output format

### Tuana implements — expression evaluator

- [ ] **Literal nodes** — wrap raw value in `RuntimeValue`:
  - `IntLitNode` → `RuntimeValue(int)`
  - `FloatLitNode` → `RuntimeValue(double)`
  - `BoolLitNode` → `RuntimeValue(boolean)`
  - `StringLitNode` → `RuntimeValue(String)`

- [ ] **`IdentNode`** → `environment.lookup(name)`

- [ ] **`BinOpNode`**
  - Evaluate left operand, evaluate right operand (left-to-right — locked decision)
  - Arithmetic: if either operand is `float`, widen the other and return `double` (Sebesta §7.4)
  - Relational (`<`, `>`, `<=`, `>=`): numeric operands only, return `boolean`
  - Equality (`==`, `!=`): matching types only; enum name equivalence check
  - Logical (`&&`, `||`): short-circuit — evaluate right only if result not yet determined (Sebesta §7.6)

- [ ] **`UnaryOpNode`**
  - `!` → evaluate operand, return negated `boolean`
  - Unary `-` → evaluate operand, return negated numeric value

- [ ] **`FuncCallNode`**
  - Look up function declaration in environment
  - Push new scope
  - Bind each argument value to the corresponding parameter name
  - Execute function body
  - Catch `ReturnException` and extract its value
  - Pop scope in `finally` — always, regardless of how body exits
  - Return the extracted value

- [ ] **`PostfixDotNode`**
  - Evaluate the left-side object (must be a process handle)
  - Read the named attribute: `state`, `burst`, `priority`, `arrival`
  - Return the attribute value as a `RuntimeValue`

### Tuana implements — semaphore runtime

- [ ] **`wait(s)`**
  - Resolve `s` to a `SemaphoreValue` from the environment
  - If `s.counter > 0` → decrement `s.counter`, current process continues
  - Else → add current `ProcessHandle` to `s.waitQueue` (FIFO — use `Queue`, not `Stack`), mark process BLOCKED, yield CPU

- [ ] **`post(s)`**
  - Resolve `s` to a `SemaphoreValue`
  - If `s.waitQueue` non-empty → pop the front process, mark it READY, do not increment counter
  - Else → increment `s.counter`

### Tuana implements — schedulers

- [ ] **SJF** (non-preemptive)
  - Pick the READY process with the smallest `burst` value
  - Once a process is RUNNING, do not re-run the scheduler until it finishes or blocks

- [ ] **SRTF** (preemptive)
  - At every tick, pick the READY process with the smallest *remaining* burst
  - If a newly READY process has smaller remaining burst than the currently RUNNING one, preempt

- [ ] **RR** (round-robin)
  - Maintain a circular ready queue
  - Track remaining quantum per process
  - On quantum expiry → move process to back of ready queue with fresh quantum
  - On mid-quantum finish → release CPU immediately, next process starts next tick
  - On mid-quantum block → process gets fresh quantum when it re-enters ready queue (locked decision from Step 0)

### Tuana implements — `add` statement runtime

- [ ] At the tick matching the `arrival` value, inject the process into the system's ready queue
- [ ] Runtime error if the `arrival` tick has already passed when `add` is executed

### Ferhat implements — statement executor + simulation engine

- [ ] `VarDeclStmtNode`, `AssignStmtNode`, `IfStmtNode`, `WhileStmtNode`, `ReturnStmtNode`, `print` handler, `BlockNode`
- [ ] Tick loop, ready queue management, FCFS scheduler, PRIORITY scheduler, tick trace printing

### Both together — integration

- [ ] Integrate all interpreter parts
- [ ] Run the full producer-consumer example end to end
- [ ] Hand-trace the first 5 ticks on paper and verify the trace output matches
- [ ] Run all valid test programs and confirm they produce sensible output

---

## STEP 5 — Final tests + individual deliverables

### Both together

- [ ] Add 3 new programs to D3:
  - One that shows a type error caught at compile time
  - One that runs with SJF or SRTF and shows the tick trace
  - One that uses an enum variable, prints it, and shows the member name in output
- [ ] Update D3 test report with new programs and their expected output
- [ ] Final end-to-end check: compile all Java files, run all programs, no crashes
- [ ] Update D6 contribution report to reflect Part 2 work split

### Tuana — individual

- [ ] **D4 AI journal** — new dated entries for Part 2 sessions; include what was accepted/rejected from each session
- [ ] **D5 retrospective** — your own reflection:
  - What was harder than expected?
  - What would you do differently if starting over?
  - What had to be scoped out?
  - What was the single hardest design decision and why?
  - Self-assessment using the rubric (A/B/C/D per dimension with one-sentence justification each)

### Ferhat — individual

- [ ] D4 AI journal entries
- [ ] D5 retrospective

---

## STEP 6 — Exam prep (Both · before 28 May)

- [ ] **Tuana**: be able to explain Ferhat's statement executor and simulation engine code
- [ ] **Ferhat**: be able to explain Tuana's type checker declaration checks, expression evaluator, and semaphore runtime
- [ ] **Both**: hand-trace the producer-consumer example for the first 5 ticks on paper
- [ ] **Both**: write the operational semantics rules for `while` and `wait`/`post` from memory
- [ ] **Both**: answer "why did you choose X over Y" for every decision in §4.8

---

## Submission file checklist

```
D1   OSlang_D1_P2.pdf
D2   source/
       TokenType.java
       Token.java
       Lexer.java
       AST.java
       Parser.java
       TypeChecker.java      ← Tuana (declarations) + Ferhat (expressions)
       Interpreter.java      ← Tuana (evaluator, semaphore, schedulers) + Ferhat (executor, engine)
       Environment.java      ← Both (agreed interface)
       RuntimeValue.java     ← Both (agreed structure)
       Main.java             ← updated
D3   OSlang_D3_P2.pdf
D4   OSlang_D4_P2_Tuana.pdf
D5   OSlang_D5_P2_Tuana.pdf
D6   OSlang_D6_P2.pdf
```

---

*Last updated: 22 May 2026*