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
    // -------------------------------------------------------------------------
    private void executeVarDecl(VarDeclStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeVarDecl not yet implemented");
    }

    // -------------------------------------------------------------------------
    // AssignStmtNode — `x <- expr;`
    // Ferhat
    // -------------------------------------------------------------------------
    private void executeAssign(AssignStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeAssign not yet implemented");
    }

    // -------------------------------------------------------------------------
    // IfStmtNode — `if (cond) { } elif (cond) { } else { }`
    // Ferhat
    // -------------------------------------------------------------------------
    private void executeIf(IfStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeIf not yet implemented");
    }

    // -------------------------------------------------------------------------
    // WhileStmtNode — `while (cond) { }`
    // Ferhat
    // -------------------------------------------------------------------------
    private void executeWhile(WhileStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeWhile not yet implemented");
    }

    // -------------------------------------------------------------------------
    // ReturnStmtNode — `return expr;`
    // Ferhat — throws ReturnException (caught by evaluate(FuncCallNode) in Tuana's half)
    // -------------------------------------------------------------------------
    private void executeReturn(ReturnStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeReturn not yet implemented");
    }

    // -------------------------------------------------------------------------
    // BlockNode — `{ stmt* }`
    // Ferhat
    // -------------------------------------------------------------------------
    private void executeBlock(BlockNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeBlock not yet implemented");
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
    // Decision §5.32: enum prints member name; semaphore prints counter
    // -------------------------------------------------------------------------
    private void executePrint(CallStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executePrint not yet implemented");
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
        int limit = 100; // default, overridden by `until`

        // Per-process: index of next statement to execute in its body
        final Map<String, Integer> programCounters = new HashMap<>();

        SimState(SystemDeclNode node) { this.systemNode = node; }
    }

    // -------------------------------------------------------------------------
    // executeRun — entry point for `run(SysName, until: N);`
    // Ferhat
    // -------------------------------------------------------------------------
    private void executeRun(RunStmtNode node) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] executeRun not yet implemented");
    }

    // -------------------------------------------------------------------------
    // Tick loop — Ferhat
    // One iteration = one CPU tick. Decision D3: one statement per tick.
    // -------------------------------------------------------------------------
    private void runSimulation(SimState sim) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] runSimulation not yet implemented");
    }

    // -------------------------------------------------------------------------
    // Ready queue management — Ferhat
    // -------------------------------------------------------------------------

    /** Move arriving processes into the ready queue at the current tick. */
    private void admitArrivals(SimState sim) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] admitArrivals not yet implemented");
    }

    /** Execute one statement from the running process body. */
    private void stepRunningProcess(SimState sim) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] stepRunningProcess not yet implemented");
    }

    // -------------------------------------------------------------------------
    // FCFS scheduler — Ferhat
    // Non-preemptive. Pick the ready process with the lowest arrival time.
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle scheduleFCFS(SimState sim) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] scheduleFCFS not yet implemented");
    }

    // -------------------------------------------------------------------------
    // PRIORITY scheduler — Ferhat
    // Non-preemptive. Pick the ready process with the highest priority value.
    // Tie-break: lowest arrival time wins (FCFS within same priority).
    // -------------------------------------------------------------------------
    private RuntimeValue.ProcessHandle schedulePRIORITY(SimState sim) {
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] schedulePRIORITY not yet implemented");
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
        // TODO Ferhat
        throw new RuntimeError("[Ferhat TODO] printTraceLine not yet implemented");
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