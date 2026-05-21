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
    //
    //   globals : flat map of every top-level name (enum, process, semaphore,
    //             function, system, static var) -> its type/info.
    //   scopes  : a stack of local scopes; one is pushed on entering a
    //             function/process body and popped on exit. Empty at top level.
    //
    // Enum and function declarations need more than a bare type, so they get
    // dedicated info records stored in their own maps, all global.
    // =========================================================================

    // Variable-like names (static vars, semaphores, and locals/params) -> type
    private final Map<String, OSlangType> globals = new HashMap<>();
    private final Deque<Map<String, OSlangType>> scopes = new ArrayDeque<>();

    // Enum declarations: name -> ordered member names (index == ordinal)
    private final Map<String, java.util.List<String>> enums = new HashMap<>();

    // Function signatures: name -> (param types, return type)
    public static class FuncSig {
        public final java.util.List<OSlangType> params;
        public final OSlangType returnType;
        public FuncSig(java.util.List<OSlangType> params, OSlangType returnType) {
            this.params = params;
            this.returnType = returnType;
        }
    }
    private final Map<String, FuncSig> functions = new HashMap<>();

    // Process declarations: name -> field map (burst/priority/arrival already validated)
    private final Map<String, ProcessDeclNode> processes = new HashMap<>();

    // System declarations: name -> node
    private final Map<String, SystemDeclNode> systems = new HashMap<>();

    // ---- scope helpers ------------------------------------------------------

    private void pushScope() { scopes.push(new HashMap<>()); }

    private void popScope()  { scopes.pop(); }

    /** Declare a variable-like name in the innermost active scope (local if any, else global). */
    private void declareVar(String name, OSlangType type) {
        Map<String, OSlangType> target = scopes.isEmpty() ? globals : scopes.peek();
        if (target.containsKey(name)) {
            throw new TypeError("'" + name + "' is already declared in this scope");
        }
        target.put(name, type);
    }

    /** Look up a variable-like name: innermost local scope first, then global. Null if not found. */
    private OSlangType lookupVar(String name) {
        for (Map<String, OSlangType> scope : scopes) {        // innermost first
            if (scope.containsKey(name)) return scope.get(name);
        }
        return globals.get(name);                              // may be null
    }

    // =========================================================================
    // Entry point — check a whole program
    // =========================================================================
    public void check(ProgramNode program) {
        // Suggested order (to be finalised when handlers are written):
        //   1. collect all enum + process + function + system declarations so
        //      forward references resolve, then
        //   2. check bodies and top-level executable statements.
        for (ASTNode item : program.declarations) {
            check(item);
        }
    }

    // =========================================================================
    // Statement / declaration dispatch — §5.38 check(node) returns nothing
    // =========================================================================
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

    // =========================================================================
    // Expression dispatch — §5.38 checkExpr(node) returns the expression's type
    // =========================================================================
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

    // =========================================================================
    // FERHAT — expression checking (§5.38, ROADMAP Phase 3)
    // =========================================================================

    private OSlangType checkIdent(IdentNode node) {
        throw todo("Ferhat", "checkIdent");
    }

    private OSlangType checkBinOp(BinOpNode node) {
        throw todo("Ferhat", "checkBinOp");
    }

    private OSlangType checkUnaryOp(UnaryOpNode node) {
        throw todo("Ferhat", "checkUnaryOp");
    }

    private OSlangType checkFuncCall(FuncCallNode node) {
        throw todo("Ferhat", "checkFuncCall");
    }

    private OSlangType checkPostfixDot(PostfixDotNode node) {
        throw todo("Ferhat", "checkPostfixDot");
    }

    // =========================================================================
    // FERHAT — statement checking (inside blocks)
    // =========================================================================

    private void checkBlock(BlockNode node) {
        throw todo("Ferhat", "checkBlock");
    }

    private void checkVarDeclStmt(VarDeclStmtNode node) {
        throw todo("Ferhat", "checkVarDeclStmt");
    }

    private void checkAssignStmt(AssignStmtNode node) {
        throw todo("Ferhat", "checkAssignStmt");
    }

    private void checkReturnStmt(ReturnStmtNode node) {
        throw todo("Ferhat", "checkReturnStmt");
    }

    private void checkIfStmt(IfStmtNode node) {
        throw todo("Ferhat", "checkIfStmt");
    }

    private void checkWhileStmt(WhileStmtNode node) {
        throw todo("Ferhat", "checkWhileStmt");
    }

    // =========================================================================
    // TUANA — declaration checking (top level)
    // =========================================================================

    private void checkStaticDecl(StaticDeclNode node) {
        throw todo("Tuana", "checkStaticDecl");
    }

    private void checkSemaphoreDecl(SemaphoreDeclNode node) {
        throw todo("Tuana", "checkSemaphoreDecl");
    }

    private void checkEnumDecl(EnumDeclNode node) {
        throw todo("Tuana", "checkEnumDecl");
    }

    private void checkProcessDecl(ProcessDeclNode node) {
        throw todo("Tuana", "checkProcessDecl");
    }

    private void checkFuncDecl(FuncDeclNode node) {
        throw todo("Tuana", "checkFuncDecl");
    }

    // =========================================================================
    // TUANA — domain-specific checks
    // =========================================================================

    private void checkCallStmt(CallStmtNode node) {
        // wait(s)/post(s): one arg, must be semaphore; print(x): one arg, any type.
        throw todo("Tuana", "checkCallStmt");
    }

    private void checkSystemDecl(SystemDeclNode node) {
        throw todo("Tuana", "checkSystemDecl");
    }

    private void checkRunStmt(RunStmtNode node) {
        throw todo("Tuana", "checkRunStmt");
    }

    private void checkAddStmt(AddStmtNode node) {
        throw todo("Tuana", "checkAddStmt");
    }
}