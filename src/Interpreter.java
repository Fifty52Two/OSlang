import java.util.*;

// =============================================================================
// Interpreter.java — Tree-walk interpreter for OSlang
//
// Work split (see README2.md §Phase 4):
//   Ferhat  — execute() statement executor, tick loop, ready queue mgmt,
//             FCFS scheduler, PRIORITY scheduler, Style-C trace output
//   Tuana   — evaluate() expression evaluator, semaphore runtime (wait/post),
//             SJF / SRTF / RR schedulers, add-statement runtime
//
// Decisions locked (see README2.md §Decisions):
//   D3 — Burst is a scheduler hint; body drives termination (one stmt/tick)
//   D4 — Style-C table trace output (TICK | RUNNING | EVENT | READY | BLOCKED)
//   D5 — ReturnException for function return propagation  ← PENDING CONFIRM
//
// Sebesta refs:
//   §3.5  — operational semantics (each tick = one small-step transition)
//   §5.5  — static scoping, resolved at type-check time
//   §9.5  — parameter passing: value for primitives, reference for semaphore/process
// =============================================================================

public class Interpreter {

    // =========================================================================
    // ReturnException — control-flow signal for `return expr;`
    // Thrown by execute(ReturnStmtNode), caught by evaluate(FuncCallNode).
    // Sebesta §3.5 — `return` is an abrupt control transfer; Java exceptions
    // are the cleanest host-language expression of that.
    // DECISION D5: PENDING CONFIRMATION — assumed Option A (ReturnException)
    // =========================================================================
    public static class ReturnException extends RuntimeException {
        public final RuntimeValue value;
        public ReturnException(RuntimeValue value) {
            super(null, null, true, false); // suppress stack trace — not a real error
            this.value = value;
        }
    }

    // =========================================================================
    // RuntimeError — thrown on any runtime violation
    // =========================================================================
    public static class RuntimeError extends RuntimeException {
        public RuntimeError(String message) { super("Runtime error: " + message); }
    }

    // =========================================================================
    // Fields
    // =========================================================================

    private final Environment env = new Environment();

    // AST nodes of all declared entities — needed by the simulation engine
    private final Map<String, FuncDeclNode>    functions  = new HashMap<>();
    private final Map<String, ProcessDeclNode> processes  = new HashMap<>();
    private final Map<String, SystemDeclNode>  systems    = new HashMap<>();

    // Enum member name → (typeName, ordinal) — needed by evaluate(IdentNode)
    private final Map<String, EnumEntry> enumMembers = new HashMap<>();
    private static class EnumEntry {
        final String typeName; final int ordinal;
        EnumEntry(String t, int o) { typeName = t; ordinal = o; }
    }

    // =========================================================================
    // Public entry point
    // =========================================================================

    public void interpret(ProgramNode program) {
        // Pass 1 — register all top-level declarations into the environment
        //          and interpreter maps (same two-pass idea as TypeChecker)
        for (ASTNode node : program.declarations) {
            registerDecl(node);
        }
        // Pass 2 — execute run/add statements (starts the simulation)
        for (ASTNode node : program.declarations) {
            if (node instanceof RunStmtNode) {
                executeRun((RunStmtNode) node);
            } else if (node instanceof AddStmtNode) {
                // TODO Tuana — executeAdd
            }
        }
    }

    // =========================================================================
    // Pass 1 — Declaration registration
    // =========================================================================

    private void registerDecl(ASTNode node) {
        if (node instanceof StaticDeclNode) {
            StaticDeclNode n = (StaticDeclNode) node;
            RuntimeValue val = evaluate(n.init);
            env.define(n.name, val);

        } else if (node instanceof SemaphoreDeclNode) {
            SemaphoreDeclNode n = (SemaphoreDeclNode) node;
            int counter = ((IntLitNode) n.init).value;
            env.define(n.name, RuntimeValue.ofSemaphore(counter));

        } else if (node instanceof EnumDeclNode) {
            EnumDeclNode n = (EnumDeclNode) node;
            for (int i = 0; i < n.members.size(); i++) {
                String member = n.members.get(i);
                enumMembers.put(member, new EnumEntry(n.name, i));
                env.define(member,
                    RuntimeValue.ofEnum(n.name, member, i));
            }

        } else if (node instanceof FuncDeclNode) {
            functions.put(((FuncDeclNode) node).name, (FuncDeclNode) node);

        } else if (node instanceof ProcessDeclNode) {
            processes.put(((ProcessDeclNode) node).name, (ProcessDeclNode) node);

        } else if (node instanceof SystemDeclNode) {
            systems.put(((SystemDeclNode) node).name, (SystemDeclNode) node);
        }
    }

    // =========================================================================
    // Statement executor — Ferhat's half
    // =========================================================================

    /**
     * Execute a single statement node. Dispatches to the correct handler.
     * Returns normally unless a ReturnException is thrown (function return).
     */
    public void execute(ASTNode node) {
        if (node instanceof VarDeclStmtNode) {
            executeVarDecl((VarDeclStmtNode) node);
        } else if (node instanceof AssignStmtNode) {
            executeAssign((AssignStmtNode) node);
        } else if (node instanceof IfStmtNode) {
            executeIf((IfStmtNode) node);
        } else if (node instanceof WhileStmtNode) {
            executeWhile((WhileStmtNode) node);
        } else if (node instanceof ReturnStmtNode) {
            executeReturn((ReturnStmtNode) node);
        } else if (node instanceof BlockNode) {
            executeBlock((BlockNode) node);
        } else if (node instanceof CallStmtNode) {
            executeCall((CallStmtNode) node);
        } else {
            throw new RuntimeError("Unknown statement node: " + node.getClass().getSimpleName());
        }
    }

    // -------------------------------------------------------------------------
    // VarDeclStmtNode — `int x <- expr;`
    // Ferhat
    //
    // Decision §5.31 (static typing): the type checker is authoritative. By
    // the time we reach this node the declared type and the initializer's
    // type are already known to match, so the interpreter just evaluates
    // the initializer and binds the name.
    //
    // Environment.define() throws if the name is already bound in the
    // current scope, so we get redeclaration-detection for free.
    //
    // Decision §5.10 (mandatory initializers): every var decl has a non-null
    // `init`, so we never need to handle the "uninitialized" case.
    // -------------------------------------------------------------------------
    private void executeVarDecl(VarDeclStmtNode node) {
        RuntimeValue value = evaluate(node.init);
        env.define(node.name, value);
    }

    // -------------------------------------------------------------------------
    // AssignStmtNode — `x <- expr;`
    // Ferhat
    //
    // Decision §5.14: assignment is a STATEMENT, never an expression — so
    // there is no chained `a <- b <- c` to worry about. The target is a
    // bare identifier (parser-enforced).
    //
    // Decision §5.9: `static` is a LIFETIME qualifier, not an access
    // qualifier. A static variable is just a global binding, so writes to
    // it go through the same scope-chain walk as any other assignment.
    //
    // Environment.assign() walks from the innermost scope outward and
    // throws if the name is not declared anywhere — that gives us the
    // "assign to undeclared variable" runtime error for free. (In practice
    // the type checker will already have rejected this case.)
    // -------------------------------------------------------------------------
    private void executeAssign(AssignStmtNode node) {
        RuntimeValue value = evaluate(node.value);
        env.assign(node.target, value);
    }

    // -------------------------------------------------------------------------
    // IfStmtNode — `if (cond) { } elif (cond) { } else { }`
    // Ferhat
    //
    // Decision §5.31 (static typing): the type checker has already verified
    // every condition expression is bool, so we read `.boolVal` directly.
    //
    // Decision §5.8 (two-level scope): no per-block scope push — we just
    // call executeBlock, which iterates statements.
    //
    // Elif/else handling: short-circuit. As soon as a branch's condition is
    // true we execute its block and return. If no branch matched and an
    // else exists, execute it. Otherwise do nothing (legal — else is
    // optional per the grammar).
    //
    // A ReturnException thrown inside any executed block propagates up
    // unchanged.
    // -------------------------------------------------------------------------
    private void executeIf(IfStmtNode node) {
        if (evaluate(node.condition).boolVal) {
            executeBlock(node.thenBlock);
            return;
        }
        for (ElifClauseNode elif : node.elifClauses) {
            if (evaluate(elif.condition).boolVal) {
                executeBlock(elif.body);
                return;
            }
        }
        if (node.elseBlock != null) {
            executeBlock(node.elseBlock);
        }
    }

    // -------------------------------------------------------------------------
    // WhileStmtNode — `while (cond) { }`
    // Ferhat
    //
    // Decision: no iteration cap. Infinite loops are the programmer's
    // responsibility — the same stance Java/C/Python take. Justification:
    //   - Process-body loops are naturally bounded by the simulation tick
    //     limit (Decision §3 hard scope: finite simulation time).
    //   - Function-body loops are intended to be short helpers; adding an
    //     arbitrary cap would penalize legitimate long loops.
    //
    // Standard pre-test loop semantics: re-evaluate the condition before
    // every iteration. A ReturnException from any iteration's body
    // propagates up unchanged and terminates the loop.
    // -------------------------------------------------------------------------
    private void executeWhile(WhileStmtNode node) {
        while (evaluate(node.condition).boolVal) {
            executeBlock(node.body);
        }
    }

    // -------------------------------------------------------------------------
    // ReturnStmtNode — `return expr;`
    // Ferhat — throws ReturnException (caught by evaluate(FuncCallNode) in Tuana's half)
    //
    // Decision D5: control-flow exception. `executeReturn` does not unwind
    // the call stack itself; it relies on Java's exception machinery to
    // pop frames until `evaluateFuncCall` catches it. This means a `return`
    // nested arbitrarily deep inside if/while/blocks just works — every
    // intervening `execute*` simply lets the exception pass through.
    //
    // Parser guarantees `node.value` is non-null: the grammar requires an
    // expression after `return` (no bare `return;`). The type checker has
    // already verified the expression's type matches the function's
    // declared return type, so we just evaluate and throw.
    // -------------------------------------------------------------------------
    private void executeReturn(ReturnStmtNode node) {
        RuntimeValue value = evaluate(node.value);
        throw new ReturnException(value);
    }

    // -------------------------------------------------------------------------
    // BlockNode — `{ stmt* }`
    // Ferhat
    //
    // Decision §5.8: two-level scope only (global + function/process local).
    // Blocks do NOT push their own scope. The single local scope is pushed
    // once at function/process entry and popped on exit. This means a name
    // declared inside { } remains visible in the rest of the function body —
    // but the type checker already forbids re-declaration of an existing
    // local name, so this cannot cause silent bugs.
    //
    // A ReturnException raised by any inner statement propagates up
    // unchanged; we do not catch it here.
    // -------------------------------------------------------------------------
    private void executeBlock(BlockNode node) {
        for (ASTNode stmt : node.statements) {
            execute(stmt);
        }
    }

    // -------------------------------------------------------------------------
    // CallStmtNode — built-ins: print, wait, post; also user func calls as stmts
    // print → Ferhat | wait/post → Tuana | user func → Tuana (calls evaluate)
    // -------------------------------------------------------------------------
    private void executeCall(CallStmtNode node) {
        switch (node.name) {
            case "print":
                executePrint(node);
                break;
            case "wait":
                executeWait(node); // TODO Tuana
                break;
            case "post":
                executePost(node); // TODO Tuana
                break;
            default:
                // User-defined function called as a statement — evaluate and discard value
                evaluate(new FuncCallNode(node.name, node.args, node.line));
                break;
        }
    }

    // -------------------------------------------------------------------------
    // print — Ferhat
    // Decision §5.32: enum prints member name; §5.35: semaphore prints counter.
    // Both are already handled by RuntimeValue.toDisplayString().
    //
    // Argument joining: NO separator between arguments — the programmer
    // controls all spacing via string literals. `print("x=", x, " y=", y);`
    // produces `x=5 y=3` with exactly the spaces the programmer wrote.
    // Rationale: predictable output, full programmer control. Matches the
    // teaching-DSL philosophy of "you see exactly what you wrote".
    //
    // A single trailing newline is added so successive print calls don't
    // run together.
    // -------------------------------------------------------------------------
    private void executePrint(CallStmtNode node) {
        StringBuilder sb = new StringBuilder();
        for (ASTNode arg : node.args) {
            sb.append(evaluate(arg).toDisplayString());
        }
        System.out.println(sb.toString());
    }

    // -------------------------------------------------------------------------
    // wait(semaphore) — Tuana
    // -------------------------------------------------------------------------
    private void executeWait(CallStmtNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] executeWait not yet implemented");
    }

    // -------------------------------------------------------------------------
    // post(semaphore) — Tuana
    // -------------------------------------------------------------------------
    private void executePost(CallStmtNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] executePost not yet implemented");
    }

    // =========================================================================
    // Expression evaluator — Tuana's half
    // Returns a RuntimeValue for any expression node.
    // =========================================================================
    public RuntimeValue evaluate(ASTNode node) {
        if (node instanceof IntLitNode)     return RuntimeValue.ofInt(((IntLitNode) node).value);
        if (node instanceof FloatLitNode)   return RuntimeValue.ofFloat(((FloatLitNode) node).value);
        if (node instanceof BoolLitNode)    return RuntimeValue.ofBool(((BoolLitNode) node).value);
        if (node instanceof StringLitNode)  return RuntimeValue.ofString(((StringLitNode) node).value);
        if (node instanceof IdentNode)      return evaluateIdent((IdentNode) node);
        if (node instanceof BinOpNode)      return evaluateBinOp((BinOpNode) node);
        if (node instanceof UnaryOpNode)    return evaluateUnaryOp((UnaryOpNode) node);
        if (node instanceof FuncCallNode)   return evaluateFuncCall((FuncCallNode) node);
        if (node instanceof PostfixDotNode) return evaluatePostfixDot((PostfixDotNode) node);
        throw new RuntimeError("Unknown expression node: " + node.getClass().getSimpleName());
    }

    private RuntimeValue evaluateIdent(IdentNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] evaluateIdent not yet implemented");
    }

    private RuntimeValue evaluateBinOp(BinOpNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] evaluateBinOp not yet implemented");
    }

    private RuntimeValue evaluateUnaryOp(UnaryOpNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] evaluateUnaryOp not yet implemented");
    }

    private RuntimeValue evaluateFuncCall(FuncCallNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] evaluateFuncCall not yet implemented");
    }

    private RuntimeValue evaluatePostfixDot(PostfixDotNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] evaluatePostfixDot not yet implemented");
    }

    // =========================================================================
    // Simulation engine — Ferhat's half
    // =========================================================================

    /**
     * Simulation state — one per running system.
     * Shared across all scheduler methods so they can read/modify process states.
     */
    private static class SimState {
        final SystemDeclNode systemNode;
        final List<RuntimeValue.ProcessHandle> allProcesses = new ArrayList<>();
        final List<RuntimeValue.ProcessHandle> readyQueue   = new ArrayList<>();
        final List<RuntimeValue.ProcessHandle> blockedList  = new ArrayList<>();
        RuntimeValue.ProcessHandle running = null;
        int tick = 0;
        int limit = 100; // default, overridden by `until` or -1 for natural termination

        // Per-process: index of next statement to execute in its body
        final Map<String, Integer> programCounters = new HashMap<>();

        // Per-process: local variable bindings, persisted across ticks.
        // On each step we push these into the shared env, execute one statement,
        // then snapshot them back out before popping.
        // Key: process displayName(). Value: the local bindings map for that process.
        final Map<String, Map<String, RuntimeValue>> processLocals = new HashMap<>();

        SimState(SystemDeclNode node) { this.systemNode = node; }
    }

    // -------------------------------------------------------------------------
    // executeRun — entry point for `run(SysName, until: N);`
    // Ferhat
    //
    // Decision §5.34 (Stop condition for `run` without `until`):
    //   - With `until: N` → run for exactly N ticks
    //   - Without `until` → run until all processes FINISHED, or deadlock
    //     (all BLOCKED with no unblock possible). No artificial tick cap.
    //
    // We use `limit = -1` inside SimState as the sentinel for "no explicit
    // until; terminate on natural completion or deadlock". runSimulation
    // interprets this.
    //
    // Process fields (burst/priority/arrival) are extracted from the
    // ProcessDeclNode's field list. The parser allows fields in any order;
    // the type checker has already verified burst is present, and that
    // values are int literals.
    //
    // Decision §5.30 (default priority): if `priority:` is omitted, it
    // defaults to 0 (lowest). §5.15: if `arrival:` is omitted, defaults to 0.
    // -------------------------------------------------------------------------
    private void executeRun(RunStmtNode node) {
        SystemDeclNode sys = systems.get(node.systemName);
        if (sys == null) {
            throw new RuntimeError("system '" + node.systemName + "' is not declared");
        }

        SimState sim = new SimState(sys);

        // Build a ProcessHandle for each process named in the system
        for (String procName : sys.processes) {
            ProcessDeclNode pd = processes.get(procName);
            if (pd == null) {
                throw new RuntimeError("process '" + procName + "' referenced in system '"
                                       + sys.name + "' is not declared");
            }

            int burst    = readIntField(pd, "burst",    -1); // mandatory; -1 means "missing" (shouldn't happen)
            int priority = readIntField(pd, "priority",  0); // §5.30 default
            int arrival  = readIntField(pd, "arrival",   0); // §5.15 default

            RuntimeValue.ProcessHandle handle =
                new RuntimeValue.ProcessHandle(procName, 0, burst, arrival, priority);

            sim.allProcesses.add(handle);
            sim.programCounters.put(handle.displayName(), 0);
        }

        // Set the tick limit from `until: N`, or -1 for "natural termination"
        if (node.until != null) {
            sim.limit = ((IntLitNode) node.until).value;
        } else {
            sim.limit = -1;
        }

        // Hand off to the tick loop
        runSimulation(sim);
    }

    /**
     * Read an int-valued field (`burst:`, `priority:`, `arrival:`) from a
     * ProcessDeclNode's field list. Returns `defaultValue` if absent.
     * The type checker has verified all field values are int literals.
     */
    private int readIntField(ProcessDeclNode pd, String fieldName, int defaultValue) {
        for (ProcessFieldNode f : pd.fields) {
            if (f.fieldName.equals(fieldName)) {
                return ((IntLitNode) f.value).value;
            }
        }
        return defaultValue;
    }

    // -------------------------------------------------------------------------
    // Tick loop — Ferhat
    // One iteration = one CPU tick. Decision D3: one statement per tick.
    //
    // Decision: ticks are 0-indexed (Silberschatz/Tanenbaum Gantt chart
    // convention). The first tick printed is tick 0.
    //
    // Decision: re-schedule every tick (Option A). The scheduler is the
    // single source of truth for "who runs this tick" — non-preemptive
    // schedulers return the same process if it's still ready; preemptive
    // schedulers (SRTF, RR) may override.
    //
    // Decision §5.34: termination conditions
    //   1. If `until: N` was given → stop when tick > N
    //   2. All processes FINISHED → stop naturally
    //   3. All non-finished processes BLOCKED with non-empty wait queues,
    //      and ready queue is empty → deadlock; print message and stop
    //
    // Order inside the loop matters:
    //   - admitArrivals first (a process arriving at tick T must be
    //     eligible at tick T)
    //   - then check termination (an `until` of N means N+1 ticks shown:
    //     tick 0..N inclusive; we stop AFTER printing tick N)
    //   - dispatch picks the runner
    //   - step executes one statement and prints the trace line
    // -------------------------------------------------------------------------
    private void runSimulation(SimState sim) {
        printTraceHeader();

        sim.tick = 0;
        while (true) {
            // 1. Move newly-arrived processes into ready
            admitArrivals(sim);

            // 2. Termination check — natural completion
            if (allFinished(sim)) {
                break;
            }

            // 3. Termination check — explicit `until: N` cap
            if (sim.limit >= 0 && sim.tick > sim.limit) {
                break;
            }

            // 4. Termination check — deadlock (no until specified)
            if (sim.limit < 0 && isDeadlocked(sim)) {
                System.out.println();
                System.out.println("DEADLOCK at tick " + sim.tick
                                   + ": all remaining processes blocked.");
                break;
            }

            // 5. Pick the runner for this tick (preemption handled inside)
            sim.running = dispatch(sim);

            // 6. Execute one step (or print idle line if nothing to run)
            if (sim.running == null) {
                printTraceLine(sim.tick, "-", "idle", sim);
            } else {
                stepRunningProcess(sim);
            }

            sim.tick++;
        }
    }

    /**
     * Dispatch to the scheduler named in the system declaration.
     * Returns the ProcessHandle that should run this tick (may be null
     * if no ready process exists).
     */
    private RuntimeValue.ProcessHandle dispatch(SimState sim) {
        switch (sim.systemNode.scheduler.name) {
            case "FCFS":     return scheduleFCFS(sim);
            case "PRIORITY": return schedulePRIORITY(sim);
            case "SJF":      return scheduleSJF(sim);
            case "SRTF":     return scheduleSRTF(sim);
            case "RR":       return scheduleRR(sim);
            default:
                throw new RuntimeError("unknown scheduler: "
                                       + sim.systemNode.scheduler.name);
        }
    }

    /** True if every process in the system is FINISHED. */
    private boolean allFinished(SimState sim) {
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            if (p.state != RuntimeValue.ProcessHandle.State.FINISHED) return false;
        }
        return true;
    }

    /**
     * Deadlock: every non-finished process is BLOCKED, the ready queue is
     * empty, and no future arrival can change that (arrival > tick exists?
     * — if yes, not deadlocked, just waiting).
     */
    private boolean isDeadlocked(SimState sim) {
        if (!sim.readyQueue.isEmpty()) return false;

        boolean anyBlocked = false;
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            switch (p.state) {
                case BLOCKED: anyBlocked = true; break;
                case READY:   return false; // shouldn't happen given ready empty, but safe
                case RUNNING: return false;
                case FINISHED: /* ignore */ break;
                default:      break;
            }
            // A process not yet arrived still has hope
            if (p.state == RuntimeValue.ProcessHandle.State.READY
                && p.arrival > sim.tick) {
                return false;
            }
        }
        // Also check for not-yet-arrived processes — they're in allProcesses
        // but not in any queue yet. If any will arrive in the future, no deadlock.
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            if (p.state != RuntimeValue.ProcessHandle.State.FINISHED
                && p.arrival > sim.tick
                && !sim.readyQueue.contains(p)
                && !sim.blockedList.contains(p)) {
                return false;
            }
        }
        return anyBlocked;
    }

    // -------------------------------------------------------------------------
    // Ready queue management — Ferhat
    // -------------------------------------------------------------------------

    /** Move arriving processes into the ready queue at the current tick.
     *
     * A process is eligible to enter the ready queue when:
     *   (a) its state is READY (not blocked/running/finished), AND
     *   (b) its arrival tick <= the current tick, AND
     *   (c) it is not already in the ready queue.
     *
     * We iterate allProcesses (≤10 by §3 hard scope) so contains() is trivial.
     * Processes are added in declaration order; the scheduler handles ordering.
     */
    private void admitArrivals(SimState sim) {
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            if (p.state == RuntimeValue.ProcessHandle.State.READY
                    && p.arrival <= sim.tick
                    && !sim.readyQueue.contains(p)) {
                sim.readyQueue.add(p);
            }
        }
    }

    /** Execute one statement from the running process body.
     *
     * Per-process local variables persist across ticks (Decision Option B):
     *   1. push a fresh local scope onto the shared env
     *   2. restore this process's saved local bindings into it
     *   3. execute the next statement (advance PC)
     *   4. snapshot bindings back, then pop the local scope
     *
     * If the statement is the last one, mark the process FINISHED and
     * remove it from the ready queue.
     *
     * Decision D3: one statement per tick. `remainingBurst` decrements
     * each tick as a scheduler hint for SJF/SRTF, regardless of whether
     * body completion is the real termination criterion.
     */
    private void stepRunningProcess(SimState sim) {
        RuntimeValue.ProcessHandle p = sim.running;
        String key = p.displayName();

        ProcessDeclNode pd = processes.get(p.name);
        List<ASTNode> stmts = pd.body.statements;
        int pc = sim.programCounters.getOrDefault(key, 0);

        // Swap in this process's local scope
        env.push();
        Map<String, RuntimeValue> locals =
            sim.processLocals.getOrDefault(key, new HashMap<>());
        env.restoreLocalBindings(locals);

        // Execute the statement at pc
        ASTNode stmt = stmts.get(pc);
        String event = describeStmt(stmt); // for trace line
        p.state = RuntimeValue.ProcessHandle.State.RUNNING;

        try {
            execute(stmt);
            pc++;
        } catch (ReturnException re) {
            // return inside a process body — treat as process finishing
            pc = stmts.size();
        }

        // Snapshot locals back before popping
        sim.processLocals.put(key, env.snapshotLocalBindings());
        env.pop();

        // Decrement remainingBurst each tick (Decision D3 — scheduler hint)
        if (p.remainingBurst > 0) p.remainingBurst--;

        // Check if process has finished all its statements
        if (pc >= stmts.size()) {
            p.state = RuntimeValue.ProcessHandle.State.FINISHED;
            sim.readyQueue.remove(p);
            sim.running = null;
            printTraceLine(sim.tick, key, event + " | FINISHED", sim);
        } else {
            sim.programCounters.put(key, pc);
            p.state = RuntimeValue.ProcessHandle.State.READY;
            printTraceLine(sim.tick, key, event, sim);
        }
    }

    /**
     * Produce a short human-readable description of a statement for the
     * trace EVENT column. Covers the most common cases; falls back to the
     * node class name for anything exotic.
     */
    private String describeStmt(ASTNode stmt) {
        if (stmt instanceof CallStmtNode) {
            CallStmtNode c = (CallStmtNode) stmt;
            if (!c.args.isEmpty()) {
                // e.g. "wait(mutex)" or "print(...)"
                return c.name + "(" + describeArg(c.args.get(0)) + ")";
            }
            return c.name + "()";
        }
        if (stmt instanceof AssignStmtNode) {
            return ((AssignStmtNode) stmt).target + " <- ...";
        }
        if (stmt instanceof VarDeclStmtNode) {
            return "decl " + ((VarDeclStmtNode) stmt).name;
        }
        if (stmt instanceof IfStmtNode)    return "if (...)";
        if (stmt instanceof WhileStmtNode) return "while (...)";
        if (stmt instanceof ReturnStmtNode) return "return";
        return stmt.getClass().getSimpleName();
    }

    /** Best-effort one-word description of an expression for the trace. */
    private String describeArg(ASTNode expr) {
        if (expr instanceof IdentNode)    return ((IdentNode) expr).name;
        if (expr instanceof IntLitNode)   return String.valueOf(((IntLitNode) expr).value);
        if (expr instanceof StringLitNode) return "\"" + ((StringLitNode) expr).value + "\"";
        return "...";
    }

    // -------------------------------------------------------------------------
    // FCFS scheduler — Ferhat
    // Non-preemptive. Pick the ready process with the lowest arrival time.
    // Tie-break: declaration order (stable sort preserves insertion order).
    //
    // Non-preemptive guard (Option A): if a process is already running and
    // still has work left, return it immediately without consulting the queue.
    // Only pick a new process when the runner slot is empty.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleFCFS(SimState sim) {
        // Non-preemptive: keep current runner if still going
        if (sim.running != null
                && sim.running.state != RuntimeValue.ProcessHandle.State.FINISHED
                && sim.running.state != RuntimeValue.ProcessHandle.State.BLOCKED) {
            return sim.running;
        }
        if (sim.readyQueue.isEmpty()) return null;
        // Pick the process with the lowest arrival time
        RuntimeValue.ProcessHandle best = null;
        for (RuntimeValue.ProcessHandle p : sim.readyQueue) {
            if (best == null || p.arrival < best.arrival) {
                best = p;
            }
        }
        sim.readyQueue.remove(best);
        return best;
    }

    // -------------------------------------------------------------------------
    // PRIORITY scheduler — Ferhat
    // Non-preemptive. Pick the ready process with the highest priority value.
    // Tie-break: lowest arrival time (FCFS within same priority level).
    //
    // Decision §5.30: higher integer = higher priority. Default priority = 0.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle schedulePRIORITY(SimState sim) {
        // Non-preemptive: keep current runner if still going
        if (sim.running != null
                && sim.running.state != RuntimeValue.ProcessHandle.State.FINISHED
                && sim.running.state != RuntimeValue.ProcessHandle.State.BLOCKED) {
            return sim.running;
        }
        if (sim.readyQueue.isEmpty()) return null;
        // Pick highest priority; tie-break on lowest arrival
        RuntimeValue.ProcessHandle best = null;
        for (RuntimeValue.ProcessHandle p : sim.readyQueue) {
            if (best == null
                    || p.priority > best.priority
                    || (p.priority == best.priority && p.arrival < best.arrival)) {
                best = p;
            }
        }
        sim.readyQueue.remove(best);
        return best;
    }

    // -------------------------------------------------------------------------
    // SJF scheduler — Tuana
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleSJF(SimState sim) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] scheduleSJF not yet implemented");
    }

    // -------------------------------------------------------------------------
    // SRTF scheduler — Tuana
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleSRTF(SimState sim) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] scheduleSRTF not yet implemented");
    }

    // -------------------------------------------------------------------------
    // RR scheduler — Tuana
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleRR(SimState sim) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] scheduleRR not yet implemented");
    }

    // -------------------------------------------------------------------------
    // executeAdd — Tuana
    // -------------------------------------------------------------------------
    private void executeAdd(AddStmtNode node) {
        // TODO Tuana
        throw new RuntimeError("[Tuana TODO] executeAdd not yet implemented");
    }

    // =========================================================================
    // Style-C trace output — Ferhat
    // Decision D4: table with columns TICK | RUNNING | EVENT | READY | BLOCKED
    //
    // Example:
    // TICK  RUNNING      EVENT                     READY-QUEUE          BLOCKED
    //    1  Producer     wait(empty) ok            [Consumer]           []
    //    2  Producer     wait(mutex) ok            [Consumer]           []
    //    3  -            Producer FINISHED         [Consumer]           []
    // =========================================================================

    private static final String TRACE_HEADER =
        String.format("%-6s %-12s %-25s %-20s %-20s",
            "TICK", "RUNNING", "EVENT", "READY-QUEUE", "BLOCKED");

    /** Print the header once at simulation start. */
    private void printTraceHeader() {
        System.out.println(TRACE_HEADER);
        System.out.println("-".repeat(TRACE_HEADER.length()));
    }

    /**
     * Print one trace line for the current tick.
     * Ferhat implements this in the tick loop.
     *
     * @param tick       current tick number
     * @param running    display name of running process, or "-" if none
     * @param event      what happened this tick (e.g. "wait(mutex) ok", "FINISHED")
     * @param sim        simulation state — used to build ready/blocked queue strings
     */
    private void printTraceLine(int tick, String running, String event, SimState sim) {
        String readyStr   = queueString(sim.readyQueue);
        String blockedStr = queueString(sim.blockedList);
        System.out.println(String.format("%-6d %-12s %-25s %-20s %-20s",
            tick, running, event, readyStr, blockedStr));
    }

    /** Build a compact queue string: "[Producer, Consumer]" or "[]" */
    private String queueString(List<RuntimeValue.ProcessHandle> queue) {
        if (queue.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queue.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(queue.get(i).displayName());
        }
        sb.append("]");
        return sb.toString();
    }
}