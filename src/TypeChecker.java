import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// =============================================================================
// TypeChecker.java — Static (compile-time) type checker for OSlang
//
// Architecture locked in README §5.37–§5.39 (Round 11):
//   §5.37  OSlangType — single class, Kind tag + optional enumName/memberCount.
//   §5.38  Dispatch  — check(node) for statements/declarations (returns nothing);
//                      checkExpr(node) for expressions (returns an OSlangType).
//   §5.39  Symbol table — flat global map for all top-level names; a scope is
//                      pushed only when entering a function/process body.
//
// Work split:
//   Ferhat — expression checking (checkExpr) + statement checking (check on
//            Assign/VarDecl/Return/If/While).
//   Tuana  — declaration checking (Static/Semaphore/Enum/Process/Func) +
//            domain-specific checks (wait/post/print, System/Run/Add).
//
// All node handlers below are stubs. They throw "not yet implemented" so the
// file compiles and runs; each owner replaces their stubs with real checks.
// =============================================================================

public class TypeChecker {

    // =========================================================================
    // §5.37 — Compile-time type representation
    //
    // One class with a Kind tag. enumName and memberCount are meaningful ONLY
    // when kind == ENUM. equals() is name-equivalence (§5.32): kinds must match,
    // and for ENUM the enumName must match too. memberCount is NOT part of
    // equality — it is only used for the §5.10 range check.
    // =========================================================================
    public static class OSlangType {

        public enum Kind { INT, FLOAT, BOOL, STRING, SEMAPHORE, PROCESS, VOID, ENUM }

        public final Kind   kind;
        public final String enumName;    // only set when kind == ENUM
        public final int    memberCount; // only set when kind == ENUM ([0, memberCount-1])

        private OSlangType(Kind kind, String enumName, int memberCount) {
            this.kind        = kind;
            this.enumName    = enumName;
            this.memberCount = memberCount;
        }

        // Shared singletons for the simple types
        public static final OSlangType INT       = new OSlangType(Kind.INT,       null, 0);
        public static final OSlangType FLOAT     = new OSlangType(Kind.FLOAT,     null, 0);
        public static final OSlangType BOOL      = new OSlangType(Kind.BOOL,      null, 0);
        public static final OSlangType STRING    = new OSlangType(Kind.STRING,    null, 0);
        public static final OSlangType SEMAPHORE = new OSlangType(Kind.SEMAPHORE, null, 0);
        public static final OSlangType PROCESS   = new OSlangType(Kind.PROCESS,   null, 0);
        public static final OSlangType VOID       = new OSlangType(Kind.VOID,      null, 0);

        public static OSlangType ofEnum(String name, int memberCount) {
            return new OSlangType(Kind.ENUM, name, memberCount);
        }

        public boolean isEnum() { return kind == Kind.ENUM; }

        // §5.32 name equivalence
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof OSlangType)) return false;
            OSlangType other = (OSlangType) o;
            if (kind != other.kind) return false;
            if (kind == Kind.ENUM) return Objects.equals(enumName, other.enumName);
            return true;
        }

        @Override
        public int hashCode() {
            return kind == Kind.ENUM ? Objects.hash(kind, enumName) : kind.hashCode();
        }

        @Override
        public String toString() {
            return kind == Kind.ENUM ? "enum " + enumName : kind.name().toLowerCase();
        }
    }

    // =========================================================================
    // Type error — thrown on any compile-time type violation
    // =========================================================================
    public static class TypeError extends RuntimeException {
        public TypeError(String message) { super("Type error: " + message); }
    }

    // =========================================================================
    // §5.39 — Symbol table
    // =========================================================================

    private final Map<String, OSlangType> globals = new HashMap<>();
    private final Deque<Map<String, OSlangType>> scopes = new ArrayDeque<>();

    private final Map<String, java.util.List<String>> enums = new HashMap<>();

    public static class FuncSig {
        public final java.util.List<OSlangType> params;
        public final OSlangType returnType;
        public FuncSig(java.util.List<OSlangType> params, OSlangType returnType) {
            this.params = params;
            this.returnType = returnType;
        }
    }
    private final Map<String, FuncSig> functions = new HashMap<>();

    private final Map<String, ProcessDeclNode> processes = new HashMap<>();

    private final Map<String, SystemDeclNode> systems = new HashMap<>();

    private void pushScope() { scopes.push(new HashMap<>()); }
    private void popScope()  { scopes.pop(); }

    private void declareVar(String name, OSlangType type) {
        Map<String, OSlangType> target = scopes.isEmpty() ? globals : scopes.peek();
        if (target.containsKey(name)) {
            throw new TypeError("'" + name + "' is already declared in this scope");
        }
        target.put(name, type);
    }

    private OSlangType lookupVar(String name) {
        for (Map<String, OSlangType> scope : scopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }
        return globals.get(name);
    }

    public void check(ProgramNode program) {
        for (ASTNode item : program.declarations) {
            check(item);
        }
    }

    private OSlangType resolveType(String typeName, int line) {
        switch (typeName) {
            case "int":    return OSlangType.INT;
            case "float":  return OSlangType.FLOAT;
            case "bool":   return OSlangType.BOOL;
            case "string": return OSlangType.STRING;
            case "semaphore": return OSlangType.SEMAPHORE;
            case "process":   return OSlangType.PROCESS;
            default:
                if (enums.containsKey(typeName)) {
                    return OSlangType.ofEnum(typeName, enums.get(typeName).size());
                }
                throw new TypeError("line " + line + ": unknown type '" + typeName + "'");
        }
    }

    public void check(ASTNode node) {
        if (node instanceof StaticDeclNode)         checkStaticDecl((StaticDeclNode) node);
        else if (node instanceof SemaphoreDeclNode) checkSemaphoreDecl((SemaphoreDeclNode) node);
        else if (node instanceof EnumDeclNode)      checkEnumDecl((EnumDeclNode) node);
        else if (node instanceof ProcessDeclNode)   checkProcessDecl((ProcessDeclNode) node);
        else if (node instanceof FuncDeclNode)      checkFuncDecl((FuncDeclNode) node);
        else if (node instanceof SystemDeclNode)    checkSystemDecl((SystemDeclNode) node);
        else if (node instanceof AddStmtNode)       checkAddStmt((AddStmtNode) node);
        else if (node instanceof RunStmtNode)       checkRunStmt((RunStmtNode) node);
        else if (node instanceof BlockNode)         checkBlock((BlockNode) node);
        else if (node instanceof VarDeclStmtNode)   checkVarDeclStmt((VarDeclStmtNode) node);
        else if (node instanceof AssignStmtNode)    checkAssignStmt((AssignStmtNode) node);
        else if (node instanceof CallStmtNode)      checkCallStmt((CallStmtNode) node);
        else if (node instanceof ReturnStmtNode)    checkReturnStmt((ReturnStmtNode) node);
        else if (node instanceof IfStmtNode)        checkIfStmt((IfStmtNode) node);
        else if (node instanceof WhileStmtNode)     checkWhileStmt((WhileStmtNode) node);
        else throw new TypeError("no statement handler for " + node.getClass().getSimpleName());
    }

    public OSlangType checkExpr(ASTNode node) {
        if (node instanceof IntLitNode)         return OSlangType.INT;
        if (node instanceof FloatLitNode)       return OSlangType.FLOAT;
        if (node instanceof BoolLitNode)        return OSlangType.BOOL;
        if (node instanceof StringLitNode)      return OSlangType.STRING;
        if (node instanceof IdentNode)          return checkIdent((IdentNode) node);
        if (node instanceof BinOpNode)          return checkBinOp((BinOpNode) node);
        if (node instanceof UnaryOpNode)        return checkUnaryOp((UnaryOpNode) node);
        if (node instanceof FuncCallNode)       return checkFuncCall((FuncCallNode) node);
        if (node instanceof PostfixDotNode)     return checkPostfixDot((PostfixDotNode) node);
        throw new TypeError("no expression handler for " + node.getClass().getSimpleName());
    }

    private static TypeError todo(String who, String what) {
        return new TypeError("[" + who + " TODO] " + what + " not yet implemented");
    }

    private OSlangType checkIdent(IdentNode node) { throw todo("Ferhat", "checkIdent"); }
    private OSlangType checkBinOp(BinOpNode node) { throw todo("Ferhat", "checkBinOp"); }
    private OSlangType checkUnaryOp(UnaryOpNode node) { throw todo("Ferhat", "checkUnaryOp"); }
    private OSlangType checkFuncCall(FuncCallNode node) { throw todo("Ferhat", "checkFuncCall"); }
    private OSlangType checkPostfixDot(PostfixDotNode node) { throw todo("Ferhat", "checkPostfixDot"); }

    private void checkBlock(BlockNode node) { throw todo("Ferhat", "checkBlock"); }
    private void checkVarDeclStmt(VarDeclStmtNode node) { throw todo("Ferhat", "checkVarDeclStmt"); }
    private void checkAssignStmt(AssignStmtNode node) { throw todo("Ferhat", "checkAssignStmt"); }
    private void checkReturnStmt(ReturnStmtNode node) { throw todo("Ferhat", "checkReturnStmt"); }
    private void checkIfStmt(IfStmtNode node) { throw todo("Ferhat", "checkIfStmt"); }
    private void checkWhileStmt(WhileStmtNode node) { throw todo("Ferhat", "checkWhileStmt"); }

    /**
     * Helper: is the initializer expression `initExpr` assignable to a variable
     * of declared type `declared`? Throws TypeError with a precise message if
     * not. SHARED across checkStaticDecl, checkVarDeclStmt, and checkAssignStmt
     * (Ferhat's two) so the §5.31 coercion table and §5.10 enum range rule live
     * in exactly one place.
     *
     * Rules (README §5.31 coercion table, §5.10 two-layer enum range check):
     *   - exact type match                              -> ok
     *   - int  -> float   (widening, no data loss)      -> ok
     *   - enum -> int     (widening, always safe)       -> ok
     *   - int  -> enum:
     *       * INT_LIT  : compile-time range check, must be in [0, memberCount-1]
     *       * other int expr : compiles, runtime range check (interpreter's job)
     *   - enum member / same-named enum var -> enum     -> ok (handled by exact
     *                                                      match: both resolve to
     *                                                      the same ENUM type)
     *   - everything else (float->int, int->bool, string->anything,
     *     enumA->enumB, float->enum, enum->float, ...)  -> compile-time error
     *
     * Sebesta §6.13 (errors detected statically or dynamically), §6.14 (coercion
     * weakens strong typing; only safe widenings allowed), §6.15 (name
     * equivalence for enums).
     */
    private void assignable(OSlangType declared, ASTNode initExpr, int line) {
        OSlangType initType = checkExpr(initExpr);

        // 1. Exact match (covers primitive==primitive and enum==same-named enum).
        if (declared.equals(initType)) return;

        // 2. int -> float widening.
        if (declared.equals(OSlangType.FLOAT) && initType.equals(OSlangType.INT)) return;

        // 3. enum -> int widening.
        if (declared.equals(OSlangType.INT) && initType.isEnum()) return;

        // 4. int -> enum (two-layer §5.10 check).
        if (declared.isEnum() && initType.equals(OSlangType.INT)) {
            if (initExpr instanceof IntLitNode) {
                int v = ((IntLitNode) initExpr).value;
                if (v < 0 || v >= declared.memberCount) {
                    throw new TypeError("line " + line + ": value " + v
                        + " is out of range for " + declared
                        + " [0, " + (declared.memberCount - 1) + "]");
                }
                return; // literal in range — ok at compile time
            }
            return; // non-literal int expression — range checked at runtime
        }

        // 5. Anything else is a compile-time type error.
        throw new TypeError("line " + line + ": cannot assign " + initType
            + " to a variable of type " + declared);
    }

    /**
     * static <decl_type> IDENT "<-" <expr> ";"
     *
     * Checks (§5.9 static lifetime, §5.31 coercion, §5.10 enum range):
     *   - the name is not already declared,
     *   - the declared type exists (resolveType throws on an unknown enum name),
     *   - the initializer is assignable to the declared type (shared helper).
     *
     * A static declaration is top-level, so the name is registered as a global.
     */
    private void checkStaticDecl(StaticDeclNode node) {
        // Step 1 — name must not already be declared.
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name
                + "' is already declared");
        }

        // Step 2 — resolve the declared type (throws if an enum name is unknown).
        OSlangType declared = resolveType(node.declType, node.line);

        // Step 3 — initializer must be assignable to the declared type.
        assignable(declared, node.init, node.line);

        // Step 4 — register the static variable as a global.
        globals.put(node.name, declared);
    }


    private void checkSemaphoreDecl(SemaphoreDeclNode node) {
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }
        if (!(node.init instanceof IntLitNode)) {
            throw new TypeError("line " + node.line + ": semaphore '" + node.name
                + "' initial value must be an integer literal");
        }
        int value = ((IntLitNode) node.init).value;
        if (value < 0) {
            throw new TypeError("line " + node.line + ": semaphore '" + node.name
                + "' initial value must be >= 0, got " + value);
        }
        globals.put(node.name, OSlangType.SEMAPHORE);
    }

    private void checkEnumDecl(EnumDeclNode node) {
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String member : node.members) {
            if (!seen.add(member)) {
                throw new TypeError("line " + node.line + ": enum '" + node.name
                    + "' has duplicate member '" + member + "'");
            }
        }
        for (String member : node.members) {
            if (globals.containsKey(member)) {
                throw new TypeError("line " + node.line + ": enum member '" + member
                    + "' collides with an already declared global name");
            }
        }
        globals.put(node.name, OSlangType.ofEnum(node.name, node.members.size()));
        enums.put(node.name, node.members);
    }

    private void checkProcessDecl(ProcessDeclNode node) {
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean hasBurst = false;
        for (ProcessFieldNode field : node.fields) {
            if (!seen.add(field.fieldName)) {
                throw new TypeError("line " + field.line + ": process '" + node.name
                    + "' has duplicate field '" + field.fieldName + "'");
            }
            if (field.fieldName.equals("burst")) hasBurst = true;
        }
        if (!hasBurst) {
            throw new TypeError("line " + node.line + ": process '" + node.name
                + "' is missing mandatory field 'burst'");
        }
        for (ProcessFieldNode field : node.fields) {
            if (!(field.value instanceof IntLitNode)) {
                throw new TypeError("line " + field.line + ": field '" + field.fieldName
                    + "' in process '" + node.name + "' must be an integer literal");
            }
            int val = ((IntLitNode) field.value).value;
            if (field.fieldName.equals("arrival")) {
                if (val < 0) {
                    throw new TypeError("line " + field.line + ": field 'arrival' in process '"
                        + node.name + "' must be >= 0, got " + val);
                }
            } else {
                if (val <= 0) {
                    throw new TypeError("line " + field.line + ": field '" + field.fieldName
                        + "' in process '" + node.name + "' must be > 0, got " + val);
                }
            }
        }
        globals.put(node.name, OSlangType.PROCESS);
        processes.put(node.name, node);
    }

    private void checkFuncDecl(FuncDeclNode node) {
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ParamNode param : node.params) {
            if (!seen.add(param.name)) {
                throw new TypeError("line " + node.line + ": function '" + node.name
                    + "' has duplicate parameter '" + param.name + "'");
            }
        }
        java.util.Set<String> validTypes = new java.util.HashSet<>(
            java.util.Arrays.asList("int", "float", "bool", "string")
        );
        if (!validTypes.contains(node.returnType)) {
            throw new TypeError("line " + node.line + ": function '" + node.name
                + "' has invalid return type '" + node.returnType + "'");
        }
        java.util.List<OSlangType> paramTypes = new java.util.ArrayList<>();
        for (ParamNode param : node.params) {
            paramTypes.add(resolveType(param.type, node.line));
        }
        OSlangType retType = resolveType(node.returnType, node.line);
        functions.put(node.name, new FuncSig(paramTypes, retType));
        globals.put(node.name, OSlangType.VOID);
    }

    // =========================================================================
    // TUANA — domain-specific checks
    //
    // Covers the four constructs that give OSlang its OS-simulation character:
    //   - call statements (built-ins wait/post/print + user functions as stmts)
    //   - system declarations
    //   - run statements
    //   - add statements
    //
    // These run AFTER all top-level declarations have been registered, so that
    // forward references resolve (a process body may call a function declared
    // later; a system may list a process declared earlier). The two-pass order
    // is coordinated by check(ProgramNode) — see note there. Each method below
    // assumes processes/functions/systems maps are already populated.
    // =========================================================================

    /**
     * Helper: can an argument of type `arg` legally appear where parameter type
     * `param` is expected? Exact match, or one of the two §5.31 widenings:
     *   int  -> float   (widening, no data loss)
     *   enum -> int     (widening, always safe)
     * Mirrors the coercion table so a legal call is not wrongly rejected.
     */
    private boolean argAssignable(OSlangType arg, OSlangType param) {
        if (arg.equals(param)) return true;
        if (param.equals(OSlangType.FLOAT) && arg.equals(OSlangType.INT)) return true; // int  -> float
        if (param.equals(OSlangType.INT)   && arg.isEnum())               return true; // enum -> int
        return false;
    }

    /**
     * Helper: is `t` acceptable where an int tick-count is required (run/add)?
     * Per §5.31 an enum widens to int, so an enum value is allowed here too.
     */
    private boolean intCompatible(OSlangType t) {
        return t.equals(OSlangType.INT) || t.isEnum();
    }

    /**
     * <call_stmt> ::= IDENT "(" [ <arg_list> ] ")" ";"
     *
     * The parser routes every IDENT-followed-by-"(" statement here, so this one
     * method must handle BOTH the three reserved built-ins AND a user-defined
     * function invoked as a statement (its return value discarded).
     *
     * Built-ins are matched by name FIRST: wait/post/print are reserved at the
     * lexer level (§5.20, §5.21) and never appear in the `functions` map, so a
     * name that is not one of them is necessarily a user-function call.
     *
     * Sebesta §9.5 — a subprogram call must agree with the declaration in the
     * number and types of its parameters.
     */
    private void checkCallStmt(CallStmtNode node) {
        switch (node.name) {
            case "wait":
            case "post": {
                // P/V operation: exactly one argument, must be a semaphore.
                if (node.args.size() != 1) {
                    throw new TypeError("line " + node.line + ": '" + node.name
                        + "' takes exactly one argument, got " + node.args.size());
                }
                OSlangType argType = checkExpr(node.args.get(0));
                if (!argType.equals(OSlangType.SEMAPHORE)) {
                    throw new TypeError("line " + node.line + ": '" + node.name
                        + "' requires a semaphore argument, got " + argType);
                }
                return;
            }
            case "print": {
                // print accepts any real (non-void) value; exactly one argument.
                if (node.args.size() != 1) {
                    throw new TypeError("line " + node.line
                        + ": 'print' takes exactly one argument, got " + node.args.size());
                }
                OSlangType argType = checkExpr(node.args.get(0));
                if (argType.equals(OSlangType.VOID)) {
                    throw new TypeError("line " + node.line
                        + ": 'print' argument has no value (void)");
                }
                return;
            }
            default: {
                // User-defined function called as a statement.
                FuncSig sig = functions.get(node.name);
                if (sig == null) {
                    throw new TypeError("line " + node.line
                        + ": call to undeclared function '" + node.name + "'");
                }
                if (node.args.size() != sig.params.size()) {
                    throw new TypeError("line " + node.line + ": function '" + node.name
                        + "' expects " + sig.params.size() + " argument(s), got "
                        + node.args.size());
                }
                for (int i = 0; i < node.args.size(); i++) {
                    OSlangType argType   = checkExpr(node.args.get(i));
                    OSlangType paramType = sig.params.get(i);
                    if (!argAssignable(argType, paramType)) {
                        throw new TypeError("line " + node.line + ": function '" + node.name
                            + "' argument " + (i + 1) + " expects " + paramType
                            + ", got " + argType);
                    }
                }
            }
        }
    }

    /**
     * system IDENT "(" "processes" ":" "[" <process_list> "]" ","
     *                  "scheduler" ":" <scheduler> ")" ";"
     *
     * Checks (§5.17, §5.18, §5.19):
     *   - the system name is not already declared,
     *   - every name in the process list refers to a declared process,
     *   - no process name appears twice in the list (a duplicate is almost
     *     certainly a typo; the language's sanctioned way to obtain a second
     *     instance is the `add` statement (§5.19), so a duplicate here is
     *     rejected for reliability — Sebesta §1.3.3),
     *   - for an RR scheduler, the quantum is a positive integer literal.
     *
     * Note on the RR quant re-check: the parser already rejects quant <= 0.
     * "quant must be positive" is a static-semantic rule that a CFG cannot
     * express (Sebesta §3.4), so the type checker re-asserts it here as its
     * proper semantic owner. The duplicated guard is deliberate, not an oversight.
     *
     * The empty-list case (processes: []) is already a parse error (§5.18), so it
     * cannot reach here.
     */
    private void checkSystemDecl(SystemDeclNode node) {
        // Step 1 — system name must not already be declared.
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name
                + "' is already declared");
        }

        // Step 2 — each listed process must exist; no duplicates in the list.
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String procName : node.processes) {
            if (!processes.containsKey(procName)) {
                throw new TypeError("line " + node.line + ": system '" + node.name
                    + "' lists undeclared process '" + procName + "'");
            }
            if (!seen.add(procName)) {
                throw new TypeError("line " + node.line + ": system '" + node.name
                    + "' lists process '" + procName + "' more than once");
            }
        }

        // Step 3 — RR quantum must be a positive integer literal (re-check; see
        //          method note above on why this is repeated from the parser).
        if (node.scheduler.name.equals("RR")) {
            if (!(node.scheduler.quant instanceof IntLitNode)) {
                throw new TypeError("line " + node.line + ": system '" + node.name
                    + "' RR quantum must be an integer literal");
            }
            int quant = ((IntLitNode) node.scheduler.quant).value;
            if (quant <= 0) {
                throw new TypeError("line " + node.line + ": system '" + node.name
                    + "' RR quantum must be > 0, got " + quant);
            }
        }

        // Step 4 — register the system.
        globals.put(node.name, OSlangType.VOID);
        systems.put(node.name, node);
    }

    /**
     * run ( IDENT [ , until : <expr> ] ) ;
     *
     * Checks (§5.18 / §5.20):
     *   - the system name refers to a declared system,
     *   - if the optional `until` clause is present, its expression is a tick
     *     count and must be int (or an enum, which widens to int per §5.31).
     */
    private void checkRunStmt(RunStmtNode node) {
        // Step 1 — system must be declared.
        if (!systems.containsKey(node.systemName)) {
            throw new TypeError("line " + node.line
                + ": run refers to undeclared system '" + node.systemName + "'");
        }

        // Step 2 — until (if present) must be int-compatible.
        if (node.until != null) {
            OSlangType untilType = checkExpr(node.until);
            if (!intCompatible(untilType)) {
                throw new TypeError("line " + node.line
                    + ": run 'until' must be an int, got " + untilType);
            }
        }
    }

    /**
     * IDENT . add ( IDENT , arrival : <expr> ) ;
     *
     * Checks (§5.19):
     *   - the target system refers to a declared system,
     *   - the injected process refers to a declared process,
     *   - the `arrival` expression is a tick count: int (or enum widening to int
     *     per §5.31).
     *
     * The runtime rule "add arrival must be >= the process's declared arrival"
     * (§5.19) is a dynamic check left to the interpreter — the value may be a
     * variable not known at compile time — so it is intentionally not enforced here.
     */
    private void checkAddStmt(AddStmtNode node) {
        // Step 1 — target system must be declared.
        if (!systems.containsKey(node.systemName)) {
            throw new TypeError("line " + node.line
                + ": add refers to undeclared system '" + node.systemName + "'");
        }

        // Step 2 — injected process must be declared.
        if (!processes.containsKey(node.processName)) {
            throw new TypeError("line " + node.line
                + ": add refers to undeclared process '" + node.processName + "'");
        }

        // Step 3 — arrival must be int-compatible.
        OSlangType arrivalType = checkExpr(node.arrival);
        if (!intCompatible(arrivalType)) {
            throw new TypeError("line " + node.line
                + ": add 'arrival' must be an int, got " + arrivalType);
        }
    }
}