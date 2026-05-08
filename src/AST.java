import java.util.List;

// =============================================================================
// AST.java — All Abstract Syntax Tree node classes for OSlang
//
// Design rationale (Sebesta §4.3):
//   The parser produces the "information required to build a parse tree."
//   These nodes form the AST — internal nodes represent constructs and
//   operators, leaves represent literals and identifiers.
//
//   Abstract base class ASTNode:
//     - Every node stores the source line number for error reporting in Part 2.
//     - dump(int indent) is abstract — forces every node to be printable,
//       satisfying the --dump-ast requirement from §5.26.
// =============================================================================

abstract class ASTNode {
    public final int line;

    protected ASTNode(int line) {
        this.line = line;
    }

    public abstract void dump(int indent);

    // Shared indentation helper — two spaces per level
    protected void pad(int indent) {
        for (int i = 0; i < indent; i++) System.out.print("  ");
    }
}

// =============================================================================
// HELPER NODES
// These are not statements or expressions on their own — they exist as
// structured sub-parts of declaration nodes.
// =============================================================================

/**
 * One field inside a process declaration header.
 * <process_field> ::= <process_field_name> ":" <expr>
 * fieldName is one of: "burst", "priority", "arrival"
 */
class ProcessFieldNode extends ASTNode {
    public final String  fieldName;
    public final ASTNode value;

    public ProcessFieldNode(String fieldName, ASTNode value, int line) {
        super(line);
        this.fieldName = fieldName;
        this.value     = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("ProcessFieldNode " + fieldName);
        value.dump(indent + 1);
    }
}

/**
 * One parameter in a function declaration.
 * <param> ::= IDENT ":" <param_type>
 */
class ParamNode extends ASTNode {
    public final String name;
    public final String type; // "int", "float", "bool", "string", "semaphore", "process"

    public ParamNode(String name, String type, int line) {
        super(line);
        this.name = name;
        this.type = type;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("ParamNode " + name + " : " + type);
    }
}

/**
 * The scheduler clause inside a system declaration.
 * <scheduler> ::= "FCFS" | "SJF" | "SRTF" | "PRIORITY"
 *               | "RR" "(" "quant" ":" INT_LIT ")"
 * quant is null for all schedulers except RR.
 */
class SchedulerNode extends ASTNode {
    public final String  name;  // "FCFS", "SJF", "SRTF", "PRIORITY", "RR"
    public final ASTNode quant; // IntLitNode for RR; null for others

    public SchedulerNode(String name, ASTNode quant, int line) {
        super(line);
        this.name  = name;
        this.quant = quant;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("SchedulerNode " + name);
        if (quant != null) quant.dump(indent + 1);
    }
}

// =============================================================================
// TOP-LEVEL NODES
// Correspond to the alternatives of <top_level_decl> in the grammar.
// =============================================================================

/**
 * Root node of every OSlang program.
 * <program> ::= <top_level_decl> { <top_level_decl> }
 */
class ProgramNode extends ASTNode {
    public final List<ASTNode> declarations;

    public ProgramNode(List<ASTNode> declarations, int line) {
        super(line);
        this.declarations = declarations;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("ProgramNode");
        for (ASTNode d : declarations) d.dump(indent + 1);
    }
}

/**
 * static <decl_type> IDENT <- <expr> ;
 * declType is "int", "float", "bool", "string", or an enum name (IDENT in
 * type position — validated by the type checker in Part 2, not the parser).
 */
class StaticDeclNode extends ASTNode {
    public final String  declType;
    public final String  name;
    public final ASTNode init;

    public StaticDeclNode(String declType, String name, ASTNode init, int line) {
        super(line);
        this.declType = declType;
        this.name     = name;
        this.init     = init;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("StaticDeclNode type=" + declType + " name=" + name);
        init.dump(indent + 1);
    }
}

/**
 * semaphore IDENT <- <expr> ;
 */
class SemaphoreDeclNode extends ASTNode {
    public final String  name;
    public final ASTNode init;

    public SemaphoreDeclNode(String name, ASTNode init, int line) {
        super(line);
        this.name = name;
        this.init = init;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("SemaphoreDeclNode name=" + name);
        init.dump(indent + 1);
    }
}

/**
 * enum IDENT { member { , member } }
 * Members are plain identifier strings. Implicit integer values (0, 1, ...)
 * are assigned by the type checker, not stored here (Sebesta §6.4.1).
 */
class EnumDeclNode extends ASTNode {
    public final String       name;
    public final List<String> members;

    public EnumDeclNode(String name, List<String> members, int line) {
        super(line);
        this.name    = name;
        this.members = members;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("EnumDeclNode name=" + name);
        for (String m : members) {
            pad(indent + 1); System.out.println("EnumMember " + m);
        }
    }
}

/**
 * process IDENT ( <process_fields> ) <block>
 */
class ProcessDeclNode extends ASTNode {
    public final String                 name;
    public final List<ProcessFieldNode> fields;
    public final BlockNode              body;

    public ProcessDeclNode(String name, List<ProcessFieldNode> fields, BlockNode body, int line) {
        super(line);
        this.name   = name;
        this.fields = fields;
        this.body   = body;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("ProcessDeclNode name=" + name);
        for (ProcessFieldNode f : fields) f.dump(indent + 1);
        body.dump(indent + 1);
    }
}

/**
 * func IDENT ( [ <param_list> ] ) -> <return_type> <block>
 */
class FuncDeclNode extends ASTNode {
    public final String          name;
    public final List<ParamNode> params;
    public final String          returnType;
    public final BlockNode       body;

    public FuncDeclNode(String name, List<ParamNode> params,
                        String returnType, BlockNode body, int line) {
        super(line);
        this.name       = name;
        this.params     = params;
        this.returnType = returnType;
        this.body       = body;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("FuncDeclNode name=" + name + " returns=" + returnType);
        for (ParamNode p : params) p.dump(indent + 1);
        body.dump(indent + 1);
    }
}

/**
 * system IDENT ( processes: [ <process_list> ] , scheduler: <scheduler> ) ;
 * processes is a list of process name strings (identifiers, not ASTNodes —
 * they are names of already-declared processes, resolved by the type checker).
 */
class SystemDeclNode extends ASTNode {
    public final String        name;
    public final List<String>  processes;
    public final SchedulerNode scheduler;

    public SystemDeclNode(String name, List<String> processes,
                          SchedulerNode scheduler, int line) {
        super(line);
        this.name      = name;
        this.processes = processes;
        this.scheduler = scheduler;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("SystemDeclNode name=" + name);
        pad(indent + 1); System.out.println("processes=" + processes);
        scheduler.dump(indent + 1);
    }
}

/**
 * IDENT . add ( IDENT , arrival : <expr> ) ;
 */
class AddStmtNode extends ASTNode {
    public final String  systemName;
    public final String  processName;
    public final ASTNode arrival;

    public AddStmtNode(String systemName, String processName, ASTNode arrival, int line) {
        super(line);
        this.systemName  = systemName;
        this.processName = processName;
        this.arrival     = arrival;
    }

    @Override public void dump(int indent) {
        pad(indent);
        System.out.println("AddStmtNode system=" + systemName + " process=" + processName);
        arrival.dump(indent + 1);
    }
}

/**
 * run ( IDENT [ , until : <expr> ] ) ;
 * until is null when the optional clause is absent.
 */
class RunStmtNode extends ASTNode {
    public final String  systemName;
    public final ASTNode until; // null if not present

    public RunStmtNode(String systemName, ASTNode until, int line) {
        super(line);
        this.systemName = systemName;
        this.until      = until;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("RunStmtNode system=" + systemName);
        if (until != null) until.dump(indent + 1);
    }
}

// =============================================================================
// STATEMENT NODES
// =============================================================================

/**
 * { { <statement> } }
 * Zero or more statements inside braces.
 */
class BlockNode extends ASTNode {
    public final List<ASTNode> statements;

    public BlockNode(List<ASTNode> statements, int line) {
        super(line);
        this.statements = statements;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("BlockNode");
        for (ASTNode s : statements) s.dump(indent + 1);
    }
}

/**
 * <decl_type> IDENT <- <expr> ;
 * Inside a block. declType may be a simple type keyword or an enum name.
 */
class VarDeclStmtNode extends ASTNode {
    public final String  declType;
    public final String  name;
    public final ASTNode init;

    public VarDeclStmtNode(String declType, String name, ASTNode init, int line) {
        super(line);
        this.declType = declType;
        this.name     = name;
        this.init     = init;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("VarDeclStmtNode type=" + declType + " name=" + name);
        init.dump(indent + 1);
    }
}

/**
 * IDENT <- <expr> ;
 */
class AssignStmtNode extends ASTNode {
    public final String  target;
    public final ASTNode value;

    public AssignStmtNode(String target, ASTNode value, int line) {
        super(line);
        this.target = target;
        this.value  = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("AssignStmtNode target=" + target);
        value.dump(indent + 1);
    }
}

/**
 * IDENT ( [ <arg_list> ] ) ;
 * Also covers wait(...), post(...), print(...) — name stores the keyword string.
 */
class CallStmtNode extends ASTNode {
    public final String        name;
    public final List<ASTNode> args;

    public CallStmtNode(String name, List<ASTNode> args, int line) {
        super(line);
        this.name = name;
        this.args = args;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("CallStmtNode " + name);
        for (ASTNode a : args) a.dump(indent + 1);
    }
}

/**
 * return <expr> ;
 * A bare return; is not allowed — every function has a non-void return type.
 */
class ReturnStmtNode extends ASTNode {
    public final ASTNode value;

    public ReturnStmtNode(ASTNode value, int line) {
        super(line);
        this.value = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("ReturnStmtNode");
        value.dump(indent + 1);
    }
}

/**
 * while ( <expr> ) <block>
 */
class WhileStmtNode extends ASTNode {
    public final ASTNode   condition;
    public final BlockNode body;

    public WhileStmtNode(ASTNode condition, BlockNode body, int line) {
        super(line);
        this.condition = condition;
        this.body      = body;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("WhileStmtNode");
        condition.dump(indent + 1);
        body.dump(indent + 1);
    }
}

/**
 * One elif clause inside an if statement.
 */
class ElifClauseNode extends ASTNode {
    public final ASTNode   condition;
    public final BlockNode body;

    public ElifClauseNode(ASTNode condition, BlockNode body, int line) {
        super(line);
        this.condition = condition;
        this.body      = body;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("ElifClauseNode");
        condition.dump(indent + 1);
        body.dump(indent + 1);
    }
}

/**
 * if ( <expr> ) <block> { elif ( <expr> ) <block> } [ else <block> ]
 * Dangling-else cannot arise — all bodies are mandatory blocks (Sebesta §3.3.1.4).
 */
class IfStmtNode extends ASTNode {
    public final ASTNode              condition;
    public final BlockNode            thenBlock;
    public final List<ElifClauseNode> elifClauses;
    public final BlockNode            elseBlock; // null if absent

    public IfStmtNode(ASTNode condition, BlockNode thenBlock,
                      List<ElifClauseNode> elifClauses, BlockNode elseBlock, int line) {
        super(line);
        this.condition   = condition;
        this.thenBlock   = thenBlock;
        this.elifClauses = elifClauses;
        this.elseBlock   = elseBlock;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("IfStmtNode");
        condition.dump(indent + 1);
        thenBlock.dump(indent + 1);
        for (ElifClauseNode e : elifClauses) e.dump(indent + 1);
        if (elseBlock != null) elseBlock.dump(indent + 1);
    }
}

// =============================================================================
// EXPRESSION NODES
// =============================================================================

/**
 * Binary operation: left op right
 * op is the operator string: "+", "-", "*", "/", "%",
 *   "==", "!=", "<", ">", "<=", ">=", "&&", "||"
 * Left-associativity is enforced by the parser loop, not by the node shape
 * (Sebesta §3.3.2 p.127 — EBNF { } does not encode associativity).
 */
class BinOpNode extends ASTNode {
    public final String  op;
    public final ASTNode left;
    public final ASTNode right;

    public BinOpNode(String op, ASTNode left, ASTNode right, int line) {
        super(line);
        this.op    = op;
        this.left  = left;
        this.right = right;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("BinOpNode " + op);
        left.dump(indent + 1);
        right.dump(indent + 1);
    }
}

/**
 * Unary operation: op operand
 * op is "!" or "-" (unary minus).
 * Right-associative — encoded by <unary_expr> recursing on itself.
 */
class UnaryOpNode extends ASTNode {
    public final String  op;
    public final ASTNode operand;

    public UnaryOpNode(String op, ASTNode operand, int line) {
        super(line);
        this.op      = op;
        this.operand = operand;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("UnaryOpNode " + op);
        operand.dump(indent + 1);
    }
}

/**
 * Postfix dot access: object.attribute
 * attribute is one of: "state", "burst", "priority", "arrival"
 * One level deep only — a.b.c does not parse (Sebesta §6.7 record access).
 */
class PostfixDotNode extends ASTNode {
    public final ASTNode object;
    public final String  attribute;

    public PostfixDotNode(ASTNode object, String attribute, int line) {
        super(line);
        this.object    = object;
        this.attribute = attribute;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("PostfixDotNode ." + attribute);
        object.dump(indent + 1);
    }
}

/**
 * Function call expression: name ( [ args ] )
 * Used inside expressions (not as a statement).
 */
class FuncCallNode extends ASTNode {
    public final String        name;
    public final List<ASTNode> args;

    public FuncCallNode(String name, List<ASTNode> args, int line) {
        super(line);
        this.name = name;
        this.args = args;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("FuncCallNode " + name);
        for (ASTNode a : args) a.dump(indent + 1);
    }
}

/**
 * Identifier reference — a variable or enum member name.
 */
class IdentNode extends ASTNode {
    public final String name;

    public IdentNode(String name, int line) {
        super(line);
        this.name = name;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("IdentNode " + name);
    }
}

/** Integer literal. Value is stored as int. */
class IntLitNode extends ASTNode {
    public final int value;

    public IntLitNode(int value, int line) {
        super(line);
        this.value = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("IntLitNode " + value);
    }
}

/** Float literal. Value is stored as double. */
class FloatLitNode extends ASTNode {
    public final double value;

    public FloatLitNode(double value, int line) {
        super(line);
        this.value = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("FloatLitNode " + value);
    }
}

/** Boolean literal — true or false. */
class BoolLitNode extends ASTNode {
    public final boolean value;

    public BoolLitNode(boolean value, int line) {
        super(line);
        this.value = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("BoolLitNode " + value);
    }
}

/** String literal. Value is the decoded string (escape sequences resolved by lexer). */
class StringLitNode extends ASTNode {
    public final String value;

    public StringLitNode(String value, int line) {
        super(line);
        this.value = value;
    }

    @Override public void dump(int indent) {
        pad(indent); System.out.println("StringLitNode \"" + value + "\"");
    }
}