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
        // name must not already be declared
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }
        // initializer must be an int literal
        if (!(node.init instanceof IntLitNode)) {
            throw new TypeError("line " + node.line + ": semaphore '" + node.name
                + "' initial value must be an integer literal");
        }
        // value must be >= 0
        int value = ((IntLitNode) node.init).value;
        if (value < 0) {
            throw new TypeError("line " + node.line + ": semaphore '" + node.name
                + "' initial value must be >= 0, got " + value);
        }
        // register in global scope as SEMAPHORE
        globals.put(node.name, OSlangType.SEMAPHORE);
    }

    private void checkEnumDecl(EnumDeclNode node) {
        // Step 1 — enum name must not already be declared
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }

        // Step 2 — no duplicate member names within this enum
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String member : node.members) {
            if (!seen.add(member)) {
                throw new TypeError("line " + node.line + ": enum '" + node.name
                    + "' has duplicate member '" + member + "'");
            }
        }

        // Step 3 — no member name collides with an existing global name
        for (String member : node.members) {
            if (globals.containsKey(member)) {
                throw new TypeError("line " + node.line + ": enum member '" + member
                    + "' collides with an already declared global name");
            }
        }

        // Step 4 — register enum name in globals
        globals.put(node.name, OSlangType.ofEnum(node.name, node.members.size()));

        // Step 5 — register members in the enums map
        enums.put(node.name, node.members);
    }

    private void checkProcessDecl(ProcessDeclNode node) {
        // Step 1 — name must not already be declared
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }
    
        // Step 2 & 3 — burst mandatory, no duplicate fields
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
    
        // Step 4 — all field values must be int literals
        //          burst/priority must be > 0, arrival must be >= 0
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
    
        // Step 5 — register in globals
        globals.put(node.name, OSlangType.PROCESS);
    
        // Step 6 — store node for domain checks later
        processes.put(node.name, node);
    }

    private void checkFuncDecl(FuncDeclNode node) {
        // Step 1 — name must not already be declared
        if (globals.containsKey(node.name)) {
            throw new TypeError("line " + node.line + ": '" + node.name + "' is already declared");
        }

        // Step 2 — parameter names must be unique
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ParamNode param : node.params) {
            if (!seen.add(param.name)) {
                throw new TypeError("line " + node.line + ": function '" + node.name
                    + "' has duplicate parameter '" + param.name + "'");
            }
        }

        // Step 3 — return type must be a valid simple type
        java.util.Set<String> validTypes = new java.util.HashSet<>(
            java.util.Arrays.asList("int", "float", "bool", "string")
        );
        if (!validTypes.contains(node.returnType)) {
            throw new TypeError("line " + node.line + ": function '" + node.name
                + "' has invalid return type '" + node.returnType + "'");
        }

        // Step 4 — build and store function signature
        java.util.List<OSlangType> paramTypes = new java.util.ArrayList<>();
        for (ParamNode param : node.params) {
            paramTypes.add(resolveType(param.type, node.line));
        }
        OSlangType retType = resolveType(node.returnType, node.line);
        functions.put(node.name, new FuncSig(paramTypes, retType));

        // Step 5 — reserve the name in globals
        globals.put(node.name, OSlangType.VOID);
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