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
//   D5 — ReturnException for function return propagation (CONFIRMED)
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
    // DECISION D5: CONFIRMED — Option A (ReturnException)
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
    // Shared simulation state — set before runSimulation/stepRunningProcess
    // so that executeWait/executePost (Tuana) can access it without changing
    // method signatures. §5.27 — semaphore wait/post need the running process
    // and simulation state. Reliability §1.3.3 — null-checked in wait/post.
    // =========================================================================
    private RuntimeValue.ProcessHandle currentProcess = null;
    private SimState currentSim = null;

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
                executeAdd((AddStmtNode) node);
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
                env.define(member, RuntimeValue.ofEnum(n.name, member, i));
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
    // Decision §5.31: trust type checker. §5.10: mandatory initializer.
    // -------------------------------------------------------------------------
    private void executeVarDecl(VarDeclStmtNode node) {
        RuntimeValue value = evaluate(node.init);
        env.define(node.name, value);
    }

    // -------------------------------------------------------------------------
    // AssignStmtNode — `x <- expr;`
    // Ferhat
    // Decision §5.14: assignment is statement only. §5.9: static is lifetime qualifier.
    // -------------------------------------------------------------------------
    private void executeAssign(AssignStmtNode node) {
        RuntimeValue value = evaluate(node.value);
        env.assign(node.target, value);
    }

    // -------------------------------------------------------------------------
    // IfStmtNode — `if (cond) { } elif (cond) { } else { }`
    // Ferhat
    // Decision §5.31: read .boolVal directly. §5.8: no per-block scope push.
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
    // Decision: no iteration cap — programmer's responsibility.
    // -------------------------------------------------------------------------
    private void executeWhile(WhileStmtNode node) {
        while (evaluate(node.condition).boolVal) {
            executeBlock(node.body);
        }
    }

    // -------------------------------------------------------------------------
    // ReturnStmtNode — `return expr;`
    // Ferhat — throws ReturnException (caught by evaluateFuncCall — Tuana)
    // Decision D5: control-flow exception confirmed.
    // -------------------------------------------------------------------------
    private void executeReturn(ReturnStmtNode node) {
        RuntimeValue value = evaluate(node.value);
        throw new ReturnException(value);
    }

    // -------------------------------------------------------------------------
    // BlockNode — `{ stmt* }`
    // Ferhat
    // Decision §5.8: no per-block scope push (two-level scope only).
    // -------------------------------------------------------------------------
    private void executeBlock(BlockNode node) {
        for (ASTNode stmt : node.statements) {
            execute(stmt);
        }
    }

    // -------------------------------------------------------------------------
    // CallStmtNode — built-ins: print, wait, post; also user func calls as stmts
    // -------------------------------------------------------------------------
    private void executeCall(CallStmtNode node) {
        switch (node.name) {
            case "print": executePrint(node); break;
            case "wait":  executeWait(node);  break;
            case "post":  executePost(node);  break;
            default:
                evaluate(new FuncCallNode(node.name, node.args, node.line));
                break;
        }
    }

    // -------------------------------------------------------------------------
    // print — Ferhat
    // Decision §5.32: enum prints member name. §5.35: semaphore prints counter.
    // No separator between args — programmer controls spacing.
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
    // P-operation (Sebesta §6.9 — semaphore as synchronization primitive)
    // §5.27 — semaphore passed by reference; SemaphoreValue is shared
    // -------------------------------------------------------------------------
    private void executeWait(CallStmtNode node) {
        RuntimeValue semVal = evaluate(node.args.get(0));
        RuntimeValue.SemaphoreValue sem = semVal.semVal;

        if (sem.counter > 0) {
            sem.counter--;
        } else {
            if (currentProcess == null) {
                throw new RuntimeError("wait() called outside of a running process");
            }
            currentProcess.state = RuntimeValue.ProcessHandle.State.BLOCKED;
            sem.waitQueue.add(currentProcess.displayName());
            currentSim.running = null;
            if (!currentSim.blockedList.contains(currentProcess)) {
                currentSim.blockedList.add(currentProcess);
            }
        }
    }

    // -------------------------------------------------------------------------
    // post(semaphore) — Tuana
    // V-operation (Sebesta §6.9)
    // §5.27 — semaphore passed by reference; SemaphoreValue is shared
    // -------------------------------------------------------------------------
    private void executePost(CallStmtNode node) {
        RuntimeValue semVal = evaluate(node.args.get(0));
        RuntimeValue.SemaphoreValue sem = semVal.semVal;

        if (!sem.waitQueue.isEmpty()) {
            String waitingName = sem.waitQueue.poll();
            for (RuntimeValue.ProcessHandle handle : currentSim.allProcesses) {
                if (handle.displayName().equals(waitingName)
                        && handle.state == RuntimeValue.ProcessHandle.State.BLOCKED) {
                    handle.state = RuntimeValue.ProcessHandle.State.READY;
                    currentSim.blockedList.remove(handle);
                    currentSim.readyQueue.add(handle);
                    break;
                }
            }
        } else {
            sem.counter++;
        }
    }

    // =========================================================================
    // Expression evaluator — Tuana's half
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

    // -------------------------------------------------------------------------
    // evaluateIdent — Tuana
    // §5.5 — static scoping: walk scope chain first, enum map as fallback
    // -------------------------------------------------------------------------
    private RuntimeValue evaluateIdent(IdentNode node) {
        try {
            return env.lookup(node.name);
        } catch (RuntimeException e) {
            EnumEntry entry = enumMembers.get(node.name);
            if (entry != null) {
                return RuntimeValue.ofEnum(entry.typeName, node.name, entry.ordinal);
            }
        }
        throw new RuntimeError("undefined identifier '" + node.name + "'");
    }

    // -------------------------------------------------------------------------
    // evaluateBinOp — Tuana
    // §7.3.1 — short-circuit for && and ||
    // §5.8   — int widens to float when mixed
    // §5.30  — semaphore↔int: compare counter
    // §5.31  — enum↔int: use ordinal
    // §5.32  — name equivalence for enum equality
    // -------------------------------------------------------------------------
    private RuntimeValue evaluateBinOp(BinOpNode node) {
        String op = node.op;

        if (op.equals("&&")) {
            RuntimeValue left = evaluate(node.left);
            if (!left.boolVal) return RuntimeValue.ofBool(false);
            return RuntimeValue.ofBool(evaluate(node.right).boolVal);
        }
        if (op.equals("||")) {
            RuntimeValue left = evaluate(node.left);
            if (left.boolVal) return RuntimeValue.ofBool(true);
            return RuntimeValue.ofBool(evaluate(node.right).boolVal);
        }

        RuntimeValue left  = evaluate(node.left);
        RuntimeValue right = evaluate(node.right);

        switch (op) {
            case "+": case "-": case "*": case "/": {
                boolean isFloat = (left.type == RuntimeValue.Type.FLOAT
                                || right.type == RuntimeValue.Type.FLOAT);
                double l = toDouble(left);
                double r = toDouble(right);
                double result;
                switch (op) {
                    case "+": result = l + r; break;
                    case "-": result = l - r; break;
                    case "*": result = l * r; break;
                    case "/":
                        if (r == 0) throw new RuntimeError("division by zero");
                        result = l / r;
                        break;
                    default: result = 0;
                }
                return isFloat ? RuntimeValue.ofFloat(result) : RuntimeValue.ofInt((int) result);
            }
            case "%":
                if (right.intVal == 0) throw new RuntimeError("modulo by zero");
                return RuntimeValue.ofInt(left.intVal % right.intVal);
            case "<":  return RuntimeValue.ofBool(toDouble(left) <  toDouble(right));
            case ">":  return RuntimeValue.ofBool(toDouble(left) >  toDouble(right));
            case "<=": return RuntimeValue.ofBool(toDouble(left) <= toDouble(right));
            case ">=": return RuntimeValue.ofBool(toDouble(left) >= toDouble(right));
            case "==": return RuntimeValue.ofBool(equalityCheck(left, right));
            case "!=": return RuntimeValue.ofBool(!equalityCheck(left, right));
            default:
                throw new RuntimeError("unknown binary operator '" + op + "'");
        }
    }

    private double toDouble(RuntimeValue v) {
        if (v.type == RuntimeValue.Type.INT)   return v.intVal;
        if (v.type == RuntimeValue.Type.FLOAT) return v.floatVal;
        if (v.type == RuntimeValue.Type.ENUM)  return v.enumOrdinal;
        throw new RuntimeError("expected numeric value, got " + v.type);
    }

    private boolean equalityCheck(RuntimeValue a, RuntimeValue b) {
        if (a.type == RuntimeValue.Type.SEMAPHORE && b.type == RuntimeValue.Type.INT)
            return a.semVal.counter == b.intVal;
        if (b.type == RuntimeValue.Type.SEMAPHORE && a.type == RuntimeValue.Type.INT)
            return b.semVal.counter == a.intVal;
        if (a.type == RuntimeValue.Type.ENUM && b.type == RuntimeValue.Type.INT)
            return a.enumOrdinal == b.intVal;
        if (b.type == RuntimeValue.Type.ENUM && a.type == RuntimeValue.Type.INT)
            return b.enumOrdinal == a.intVal;
        switch (a.type) {
            case INT:    return a.intVal    == b.intVal;
            case FLOAT:  return a.floatVal  == b.floatVal;
            case BOOL:   return a.boolVal   == b.boolVal;
            case STRING: return a.stringVal.equals(b.stringVal);
            case ENUM:   return a.enumTypeName.equals(b.enumTypeName)
                             && a.enumOrdinal == b.enumOrdinal;
            default:
                throw new RuntimeError("cannot compare values of type " + a.type);
        }
    }

    // -------------------------------------------------------------------------
    // evaluateUnaryOp — Tuana
    // §5.12 — ! requires bool; - requires numeric, preserves type
    // -------------------------------------------------------------------------
    private RuntimeValue evaluateUnaryOp(UnaryOpNode node) {
        RuntimeValue operand = evaluate(node.operand);
        switch (node.op) {
            case "!": return RuntimeValue.ofBool(!operand.boolVal);
            case "-":
                if (operand.type == RuntimeValue.Type.INT)
                    return RuntimeValue.ofInt(-operand.intVal);
                return RuntimeValue.ofFloat(-operand.floatVal);
            default:
                throw new RuntimeError("unknown unary operator '" + node.op + "'");
        }
    }

    // -------------------------------------------------------------------------
    // evaluateFuncCall — Tuana
    // §9.5 — args evaluated in caller scope before push
    // §5.39 — new scope pushed for body; always popped in finally
    // D5 — ReturnException catches return value
    // -------------------------------------------------------------------------
    private RuntimeValue evaluateFuncCall(FuncCallNode node) {
        FuncDeclNode decl = functions.get(node.name);
        if (decl == null) {
            throw new RuntimeError("call to undeclared function '" + node.name + "'");
        }

        List<RuntimeValue> argValues = new ArrayList<>();
        for (int i = 0; i < node.args.size(); i++) {
            RuntimeValue argVal = evaluate(node.args.get(i));
            String declaredParamType = decl.params.get(i).type;
            if (declaredParamType.equals("float") && argVal.type == RuntimeValue.Type.INT) {
                argVal = RuntimeValue.ofFloat((double) argVal.intVal);
            } else if (declaredParamType.equals("int") && argVal.type == RuntimeValue.Type.ENUM) {
                argVal = RuntimeValue.ofInt(argVal.enumOrdinal);
            }
            argValues.add(argVal);
        }

        env.push();
        try {
            for (int i = 0; i < decl.params.size(); i++) {
                env.define(decl.params.get(i).name, argValues.get(i));
            }
            for (ASTNode stmt : decl.body.statements) {
                execute(stmt);
            }
            throw new RuntimeError("function '" + node.name + "' did not return a value");
        } catch (ReturnException ret) {
            return ret.value;
        } finally {
            env.pop();
        }
    }

    // -------------------------------------------------------------------------
    // evaluatePostfixDot — Tuana
    // §5.26 — closed attribute set: state, burst, priority, arrival
    // -------------------------------------------------------------------------
    private RuntimeValue evaluatePostfixDot(PostfixDotNode node) {
        RuntimeValue obj = evaluate(node.object);
        if (obj.type != RuntimeValue.Type.PROCESS) {
            throw new RuntimeError("'." + node.attribute + "' requires a process, got " + obj.type);
        }
        RuntimeValue.ProcessHandle handle = obj.procVal;
        switch (node.attribute) {
            case "state": {
                int ordinal = handle.state.ordinal();
                String[] stateNames = {"ready", "running", "blocked", "finished"};
                if (enumMembers.containsKey("ready")) {
                    return RuntimeValue.ofEnum("State", stateNames[ordinal], ordinal);
                }
                return RuntimeValue.ofInt(ordinal);
            }
            case "burst":    return RuntimeValue.ofInt(handle.burst);
            case "priority": return RuntimeValue.ofInt(handle.priority);
            case "arrival":  return RuntimeValue.ofInt(handle.arrival);
            default:
                throw new RuntimeError("unknown process attribute '" + node.attribute + "'");
        }
    }

    // =========================================================================
    // Simulation engine — Ferhat's half
    // =========================================================================

    private static class SimState {
        final SystemDeclNode systemNode;
        final List<RuntimeValue.ProcessHandle> allProcesses = new ArrayList<>();
        final List<RuntimeValue.ProcessHandle> readyQueue   = new ArrayList<>();
        final List<RuntimeValue.ProcessHandle> blockedList  = new ArrayList<>();
        RuntimeValue.ProcessHandle running = null;
        int tick = 0;
        int limit = -1; // -1 = natural termination; >= 0 = explicit until cap

        final Map<String, Integer> programCounters = new HashMap<>();
        final Map<String, Map<String, RuntimeValue>> processLocals = new HashMap<>();

        SimState(SystemDeclNode node) { this.systemNode = node; }
    }

    // -------------------------------------------------------------------------
    // executeRun — Ferhat
    // Decision §5.34: -1 = natural termination (all finished or deadlock)
    // Decision §5.30: default priority 0. §5.15: default arrival 0.
    // -------------------------------------------------------------------------
    private void executeRun(RunStmtNode node) {
        SystemDeclNode sys = systems.get(node.systemName);
        if (sys == null) {
            throw new RuntimeError("system '" + node.systemName + "' is not declared");
        }

        SimState sim = new SimState(sys);

        for (String procName : sys.processes) {
            ProcessDeclNode pd = processes.get(procName);
            if (pd == null) {
                throw new RuntimeError("process '" + procName + "' referenced in system '"
                                       + sys.name + "' is not declared");
            }
            int burst    = readIntField(pd, "burst",    -1);
            int priority = readIntField(pd, "priority",  0);
            int arrival  = readIntField(pd, "arrival",   0);

            RuntimeValue.ProcessHandle handle =
                new RuntimeValue.ProcessHandle(procName, 0, burst, arrival, priority);
            sim.allProcesses.add(handle);
            sim.programCounters.put(handle.displayName(), 0);
        }

        sim.limit = (node.until != null) ? ((IntLitNode) node.until).value : -1;

        // Set shared sim state for Tuana's wait/post before entering loop
        this.currentSim = sim;

        runSimulation(sim);

        this.currentSim = null;
    }

    private int readIntField(ProcessDeclNode pd, String fieldName, int defaultValue) {
        for (ProcessFieldNode f : pd.fields) {
            if (f.fieldName.equals(fieldName)) {
                return ((IntLitNode) f.value).value;
            }
        }
        return defaultValue;
    }

    // -------------------------------------------------------------------------
    // runSimulation — Ferhat
    // Decision D3: one statement per tick. Ticks 0-indexed.
    // Decision: re-schedule every tick (Option A).
    // Decision §5.34: stop on all-finished, until-cap, or deadlock.
    // -------------------------------------------------------------------------
    private void runSimulation(SimState sim) {
        printTraceHeader();
        sim.tick = 0;

        while (true) {
            admitArrivals(sim);

            if (allFinished(sim)) break;

            if (sim.limit >= 0 && sim.tick > sim.limit) break;

            if (sim.limit < 0 && isDeadlocked(sim)) {
                System.out.println();
                System.out.println("DEADLOCK at tick " + sim.tick
                                   + ": all remaining processes blocked.");
                break;
            }

            sim.running = dispatch(sim);

            if (sim.running == null) {
                printTraceLine(sim.tick, "-", "idle", sim);
            } else {
                // Set currentProcess for Tuana's wait/post
                this.currentProcess = sim.running;
                stepRunningProcess(sim);
                this.currentProcess = null;
            }

            sim.tick++;
        }
    }

    private RuntimeValue.ProcessHandle dispatch(SimState sim) {
        switch (sim.systemNode.scheduler.name) {
            case "FCFS":     return scheduleFCFS(sim);
            case "PRIORITY": return schedulePRIORITY(sim);
            case "SJF":      return scheduleSJF(sim);
            case "SRTF":     return scheduleSRTF(sim);
            case "RR":       return scheduleRR(sim);
            default:
                throw new RuntimeError("unknown scheduler: " + sim.systemNode.scheduler.name);
        }
    }

    private boolean allFinished(SimState sim) {
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            if (p.state != RuntimeValue.ProcessHandle.State.FINISHED) return false;
        }
        return true;
    }

    private boolean isDeadlocked(SimState sim) {
        if (!sim.readyQueue.isEmpty()) return false;
        boolean anyBlocked = false;
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            if (p.state == RuntimeValue.ProcessHandle.State.BLOCKED)  { anyBlocked = true; }
            if (p.state == RuntimeValue.ProcessHandle.State.READY)    { return false; }
            if (p.state == RuntimeValue.ProcessHandle.State.RUNNING)  { return false; }
        }
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
    // admitArrivals — Ferhat
    // -------------------------------------------------------------------------
    private void admitArrivals(SimState sim) {
        for (RuntimeValue.ProcessHandle p : sim.allProcesses) {
            if (p.state == RuntimeValue.ProcessHandle.State.READY
                    && p.arrival <= sim.tick
                    && !sim.readyQueue.contains(p)) {
                sim.readyQueue.add(p);
            }
        }
    }

    // -------------------------------------------------------------------------
    // stepRunningProcess — Ferhat
    // Per-process locals persisted across ticks via snapshot/restore (Option B).
    // Decision D3: one statement per tick, remainingBurst decrements as hint.
    // -------------------------------------------------------------------------
    private void stepRunningProcess(SimState sim) {
        RuntimeValue.ProcessHandle p = sim.running;
        String key = p.displayName();

        ProcessDeclNode pd = processes.get(p.name);
        List<ASTNode> stmts = pd.body.statements;
        int pc = sim.programCounters.getOrDefault(key, 0);

        env.push();
        Map<String, RuntimeValue> locals =
            sim.processLocals.getOrDefault(key, new HashMap<>());
        env.restoreLocalBindings(locals);

        ASTNode stmt = stmts.get(pc);
        String event = describeStmt(stmt);
        p.state = RuntimeValue.ProcessHandle.State.RUNNING;

        try {
            execute(stmt);
            pc++;
        } catch (ReturnException re) {
            pc = stmts.size();
        }

        sim.processLocals.put(key, env.snapshotLocalBindings());
        env.pop();

        if (p.remainingBurst > 0) p.remainingBurst--;

        // If process blocked during this step (executeWait set state to BLOCKED)
        // do not mark finished — just print the trace and leave state as BLOCKED
        if (p.state == RuntimeValue.ProcessHandle.State.BLOCKED) {
            sim.programCounters.put(key, pc);
            printTraceLine(sim.tick, key, event + " BLOCKED", sim);
        } else if (pc >= stmts.size()) {
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

    private String describeStmt(ASTNode stmt) {
        if (stmt instanceof CallStmtNode) {
            CallStmtNode c = (CallStmtNode) stmt;
            if (!c.args.isEmpty()) return c.name + "(" + describeArg(c.args.get(0)) + ")";
            return c.name + "()";
        }
        if (stmt instanceof AssignStmtNode)  return ((AssignStmtNode) stmt).target + " <- ...";
        if (stmt instanceof VarDeclStmtNode) return "decl " + ((VarDeclStmtNode) stmt).name;
        if (stmt instanceof IfStmtNode)      return "if (...)";
        if (stmt instanceof WhileStmtNode)   return "while (...)";
        if (stmt instanceof ReturnStmtNode)  return "return";
        return stmt.getClass().getSimpleName();
    }

    private String describeArg(ASTNode expr) {
        if (expr instanceof IdentNode)     return ((IdentNode) expr).name;
        if (expr instanceof IntLitNode)    return String.valueOf(((IntLitNode) expr).value);
        if (expr instanceof StringLitNode) return "\"" + ((StringLitNode) expr).value + "\"";
        return "...";
    }

    // -------------------------------------------------------------------------
    // scheduleFCFS — Ferhat
    // Non-preemptive. Lowest arrival time. Tie-break: declaration order.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleFCFS(SimState sim) {
        if (sim.running != null
                && sim.running.state != RuntimeValue.ProcessHandle.State.FINISHED
                && sim.running.state != RuntimeValue.ProcessHandle.State.BLOCKED) {
            return sim.running;
        }
        if (sim.readyQueue.isEmpty()) return null;
        RuntimeValue.ProcessHandle best = null;
        for (RuntimeValue.ProcessHandle p : sim.readyQueue) {
            if (best == null || p.arrival < best.arrival) best = p;
        }
        sim.readyQueue.remove(best);
        return best;
    }

    // -------------------------------------------------------------------------
    // schedulePRIORITY — Ferhat
    // Non-preemptive. Highest priority. Tie-break: lowest arrival.
    // Decision §5.30: higher integer = higher priority.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle schedulePRIORITY(SimState sim) {
        if (sim.running != null
                && sim.running.state != RuntimeValue.ProcessHandle.State.FINISHED
                && sim.running.state != RuntimeValue.ProcessHandle.State.BLOCKED) {
            return sim.running;
        }
        if (sim.readyQueue.isEmpty()) return null;
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
    // scheduleSJF — Tuana
    // Non-preemptive. Smallest burst. Tie-break: lowest arrival.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleSJF(SimState sim) {
        if (sim.running != null
                && sim.running.state != RuntimeValue.ProcessHandle.State.FINISHED
                && sim.running.state != RuntimeValue.ProcessHandle.State.BLOCKED) {
            return sim.running;
        }
        RuntimeValue.ProcessHandle best = null;
        for (RuntimeValue.ProcessHandle p : sim.readyQueue) {
            if (best == null) { best = p; continue; }
            if (p.burst < best.burst) { best = p; }
            else if (p.burst == best.burst && p.arrival < best.arrival) { best = p; }
        }
        if (best != null) sim.readyQueue.remove(best);
        return best;
    }

    // -------------------------------------------------------------------------
    // scheduleSRTF — Tuana
    // Preemptive. Smallest remainingBurst. Runner competes every tick.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleSRTF(SimState sim) {
        List<RuntimeValue.ProcessHandle> candidates = new ArrayList<>(sim.readyQueue);
        if (sim.running != null
                && sim.running.state == RuntimeValue.ProcessHandle.State.RUNNING) {
            candidates.add(sim.running);
        }
        RuntimeValue.ProcessHandle best = null;
        for (RuntimeValue.ProcessHandle p : candidates) {
            if (best == null) { best = p; continue; }
            if (p.remainingBurst < best.remainingBurst) { best = p; }
            else if (p.remainingBurst == best.remainingBurst && p.arrival < best.arrival) { best = p; }
        }
        if (best != null && best != sim.running && sim.running != null) {
            sim.running.state = RuntimeValue.ProcessHandle.State.READY;
            sim.readyQueue.add(sim.running);
            sim.running = null;
        }
        if (best != null) sim.readyQueue.remove(best);
        return best;
    }

    // -------------------------------------------------------------------------
    // scheduleRR — Tuana
    // Round-robin with fixed quantum. Exhausted process goes to back of queue.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleRR(SimState sim) {
        int quantum = ((IntLitNode) sim.systemNode.scheduler.quant).value;

        if (sim.running != null
                && sim.running.state == RuntimeValue.ProcessHandle.State.RUNNING) {
            sim.running.remainingQuantum--;
            if (sim.running.remainingQuantum <= 0) {
                sim.running.state = RuntimeValue.ProcessHandle.State.READY;
                sim.readyQueue.add(sim.running);
                sim.running = null;
            } else {
                return sim.running;
            }
        }

        if (sim.readyQueue.isEmpty()) return null;
        RuntimeValue.ProcessHandle next = sim.readyQueue.remove(0);
        next.remainingQuantum = quantum;
        return next;
    }

    // -------------------------------------------------------------------------
    // executeAdd — Tuana
    // §5.19: injects a second independent instance. §5.35: display name P1#1.
    // -------------------------------------------------------------------------
    private void executeAdd(AddStmtNode node) {
        SystemDeclNode sysDecl = systems.get(node.systemName);
        if (sysDecl == null) {
            throw new RuntimeError("add refers to undeclared system '" + node.systemName + "'");
        }
        ProcessDeclNode procDecl = processes.get(node.processName);
        if (procDecl == null) {
            throw new RuntimeError("add refers to undeclared process '" + node.processName + "'");
        }

        RuntimeValue arrivalVal = evaluate(node.arrival);
        int arrivalTick = (arrivalVal.type == RuntimeValue.Type.ENUM)
            ? arrivalVal.enumOrdinal : arrivalVal.intVal;

        if (currentSim != null && arrivalTick < currentSim.tick) {
            throw new RuntimeError("add: arrival tick " + arrivalTick
                + " has already passed (current tick is " + currentSim.tick + ")");
        }

        int burst = 1, priority = 0;
        for (ProcessFieldNode field : procDecl.fields) {
            int val = ((IntLitNode) field.value).value;
            switch (field.fieldName) {
                case "burst":    burst    = val; break;
                case "priority": priority = val; break;
            }
        }

        int instanceId = 0;
        if (currentSim != null) {
            for (RuntimeValue.ProcessHandle h : currentSim.allProcesses) {
                if (h.name.equals(node.processName)) instanceId++;
            }
        }

        RuntimeValue.ProcessHandle newHandle = new RuntimeValue.ProcessHandle(
            node.processName, instanceId, burst, arrivalTick, priority
        );
        newHandle.state = RuntimeValue.ProcessHandle.State.READY;

        if (currentSim != null) {
            currentSim.allProcesses.add(newHandle);
            currentSim.programCounters.put(newHandle.displayName(), 0);
        }
    }

    // =========================================================================
    // Style-C trace output — Ferhat
    // Decision D4: TICK(6) RUNNING(12) EVENT(25) READY-QUEUE(20) BLOCKED(20)
    // Ticks 0-indexed (Silberschatz/Tanenbaum convention).
    // =========================================================================

    private static final String TRACE_HEADER =
        String.format("%-6s %-12s %-25s %-20s %-20s",
            "TICK", "RUNNING", "EVENT", "READY-QUEUE", "BLOCKED");

    private void printTraceHeader() {
        System.out.println(TRACE_HEADER);
        System.out.println("-".repeat(TRACE_HEADER.length()));
    }

    private void printTraceLine(int tick, String running, String event, SimState sim) {
        System.out.println(String.format("%-6d %-12s %-25s %-20s %-20s",
            tick, running, event, queueString(sim.readyQueue), queueString(sim.blockedList)));
    }

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