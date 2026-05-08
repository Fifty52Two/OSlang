import java.util.ArrayList;
import java.util.List;

// =============================================================================
// Parser.java — Recursive-descent parser for OSlang
//
// Design rationale (Sebesta §4.4):
//   "A recursive-descent parser is so named because it consists of a
//   collection of subprograms, many of which are recursive, and it produces
//   a parse tree in top-down order."
//
//   One method per non-terminal in the EBNF grammar (§5.24 Steps 1–5).
//   EBNF { } repetition → while loop in the method.
//   EBNF [ ] optional   → if check in the method.
//   EBNF ( A | B )      → if/else chain on the current token.
//
// LL(2) point (documented in §5.26):
//   <assign_stmt> and <call_stmt> both start with IDENT.
//   Disambiguated by peeking the second token:
//     IDENT + ARROW   → assignment
//     IDENT + LPAREN  → call
//     IDENT + IDENT   → var decl with enum type
// =============================================================================

public class Parser {

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // -------------------------------------------------------------------------
    // Cursor primitives
    // -------------------------------------------------------------------------

    /** Current token without consuming. */
    private Token peek() {
        return tokens.get(pos);
    }

    /** One token ahead without consuming — used for LL(2) disambiguation. */
    private Token peek2() {
        int next = pos + 1;
        return next < tokens.size() ? tokens.get(next) : tokens.get(tokens.size() - 1);
    }

    /** Consume and return the current token. */
    private Token advance() {
        Token t = tokens.get(pos);
        if (pos < tokens.size() - 1) pos++;
        return t;
    }

    /** True if the current token is of the given type. */
    private boolean check(TokenType type) {
        return peek().type == type;
    }

    /**
     * Consume the current token if it matches type, otherwise throw a parser error.
     * This is the primary error-reporting mechanism (Sebesta §4.4 — syntax errors
     * are reported as soon as the mismatch is detected in a left-to-right scan).
     */
    private Token expect(TokenType type) {
        Token t = peek();
        if (t.type != type) {
            throw new RuntimeException(
                "[Parser Error] Line " + t.line +
                ": Expected " + type + " but found '" + t.value + "'");
        }
        return advance();
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Parse the entire token stream and return the root ProgramNode.
     * <program> ::= <top_level_decl> { <top_level_decl> }
     *
     * An empty program (only EOF) is a syntax error — every OSlang program
     * must contain at least one declaration (§5.24 Step 1).
     */
    public ProgramNode parseProgram() {
        int startLine = peek().line;
        List<ASTNode> declarations = new ArrayList<>();

        // Empty program check
        if (check(TokenType.EOF)) {
            throw new RuntimeException(
                "[Parser Error] Line 1: Empty program — at least one declaration is required");
        }

        // One or more top-level declarations
        while (!check(TokenType.EOF)) {
            declarations.add(parseTopLevelDecl());
        }

        return new ProgramNode(declarations, startLine);
    }

    // -------------------------------------------------------------------------
    // Top-level dispatch
    // -------------------------------------------------------------------------

    /**
     * Dispatch to the correct top-level parser based on the current token.
     *
     * <top_level_decl> ::= <static_decl>
     *                    | <semaphore_decl>
     *                    | <enum_decl>
     *                    | <process_decl>
     *                    | <func_decl>
     *                    | <system_decl>
     *                    | <add_stmt>
     *                    | <run_stmt>
     *
     * Disambiguation:
     *   KW_STATIC     → static_decl
     *   KW_SEMAPHORE  → semaphore_decl
     *   KW_ENUM       → enum_decl
     *   KW_PROCESS    → process_decl
     *   KW_FUNC       → func_decl
     *   KW_SYSTEM     → system_decl
     *   KW_RUN        → run_stmt
     *   IDENT + DOT   → add_stmt  (IDENT.add(...))
     *
     * All of these have a unique leading token — no lookahead ambiguity here.
     */
    private ASTNode parseTopLevelDecl() {
        Token t = peek();

        switch (t.type) {
            case KW_STATIC:    return parseStaticDecl();
            case KW_SEMAPHORE: return parseSemaphoreDecl();
            case KW_ENUM:      return parseEnumDecl();
            case KW_PROCESS:   return parseProcessDecl();
            case KW_FUNC:      return parseFuncDecl();
            case KW_SYSTEM:    return parseSystemDecl();
            case KW_RUN:       return parseRunStmt();
            case IDENT:
                // Only add_stmt starts with IDENT at top level: IDENT.add(...)
                // Peek ahead to confirm the DOT — give a clear error if not
                if (peek2().type == TokenType.DOT) return parseAddStmt();
                throw new RuntimeException(
                    "[Parser Error] Line " + t.line +
                    ": Unexpected identifier '" + t.value +
                    "' at top level. Did you mean to use 'static', 'process', 'func', or 'system'?");
            default:
                throw new RuntimeException(
                    "[Parser Error] Line " + t.line +
                    ": Unexpected token '" + t.value + "' at top level");
        }
    }

    // -------------------------------------------------------------------------
    // Declaration parsers — Step 3
    // -------------------------------------------------------------------------

    /**
     * static <decl_type> IDENT <- <expr> ;
     * <decl_type> ::= <simple_type> | IDENT
     * simple_type tokens: KW_INT, KW_FLOAT, KW_BOOL, KW_STRING
     * IDENT in type position = enum type name, validated by type checker (Part 2)
     */
    private ASTNode parseStaticDecl() {
        int line = peek().line;
        expect(TokenType.KW_STATIC);

        // Parse <decl_type> — simple type keyword or IDENT (enum name)
        String declType = parseDeclType();

        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.ARROW);
        ASTNode init = parseExpr();
        expect(TokenType.SEMICOLON);

        return new StaticDeclNode(declType, nameTok.value, init, line);
    }

    /**
     * semaphore IDENT <- <expr> ;
     */
    private ASTNode parseSemaphoreDecl() {
        int line = peek().line;
        expect(TokenType.KW_SEMAPHORE);
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.ARROW);
        ASTNode init = parseExpr();
        expect(TokenType.SEMICOLON);

        return new SemaphoreDeclNode(nameTok.value, init, line);
    }

    /**
     * enum IDENT { IDENT { , IDENT } }
     * At least one member required — empty body is a parse error.
     * Trailing comma NOT allowed — grammar rule: IDENT { "," IDENT }
     */
    private ASTNode parseEnumDecl() {
        int line = peek().line;
        expect(TokenType.KW_ENUM);
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.LBRACE);

        List<String> members = new ArrayList<>();

        // At least one member required
        Token first = peek();
        if (first.type == TokenType.RBRACE) {
            throw new RuntimeException(
                "[Parser Error] Line " + first.line +
                ": Enum '" + nameTok.value + "' must have at least one member");
        }

        members.add(expect(TokenType.IDENT).value);

        // Zero or more additional members: { "," IDENT }
        while (check(TokenType.COMMA)) {
            advance(); // consume ","
            // Guard against trailing comma: next must be IDENT
            Token next = peek();
            if (next.type == TokenType.RBRACE) {
                throw new RuntimeException(
                    "[Parser Error] Line " + next.line +
                    ": Trailing comma not allowed in enum '" + nameTok.value + "'");
            }
            members.add(expect(TokenType.IDENT).value);
        }

        expect(TokenType.RBRACE);
        // No semicolon after enum closing brace — per grammar Step 2

        return new EnumDeclNode(nameTok.value, members, line);
    }

    /**
     * process IDENT ( <process_fields> ) <block>
     * <process_fields> ::= <process_field> { "," <process_field> }
     * <process_field>  ::= <process_field_name> ":" <expr>
     * <process_field_name> ::= "burst" | "priority" | "arrival"
     *
     * Closed field set — any other token in field-name position is a parse error.
     * Constraints deferred to type checker: burst mandatory, no duplicates.
     */
    private ASTNode parseProcessDecl() {
        int line = peek().line;
        expect(TokenType.KW_PROCESS);
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.LPAREN);

        List<ProcessFieldNode> fields = new ArrayList<>();

        // At least one field required
        fields.add(parseProcessField());

        while (check(TokenType.COMMA)) {
            advance(); // consume ","
            fields.add(parseProcessField());
        }

        expect(TokenType.RPAREN);
        BlockNode body = parseBlock();

        return new ProcessDeclNode(nameTok.value, fields, body, line);
    }

    /**
     * Helper: parse one process field.
     * <process_field> ::= ("burst" | "priority" | "arrival") ":" <expr>
     */
    private ProcessFieldNode parseProcessField() {
        int line = peek().line;
        Token fieldTok = peek();

        String fieldName;
        if (fieldTok.type == TokenType.KW_BURST) {
            fieldName = "burst";
        } else if (fieldTok.type == TokenType.KW_PRIORITY_F) {
            fieldName = "priority";
        } else if (fieldTok.type == TokenType.KW_ARRIVAL) {
            fieldName = "arrival";
        } else {
            throw new RuntimeException(
                "[Parser Error] Line " + fieldTok.line +
                ": Unknown process field '" + fieldTok.value +
                "'. Expected 'burst', 'priority', or 'arrival'");
        }
        advance(); // consume the field keyword

        expect(TokenType.COLON);
        ASTNode value = parseExpr();

        return new ProcessFieldNode(fieldName, value, line);
    }

    /**
     * func IDENT ( [ <param_list> ] ) -> <return_type> <block>
     * <param_list>  ::= <param> { "," <param> }
     * <param>       ::= IDENT ":" <param_type>
     * <return_type> ::= "int" | "float" | "bool" | "string"
     * Empty parameter list is allowed.
     */
    private ASTNode parseFuncDecl() {
        int line = peek().line;
        expect(TokenType.KW_FUNC);
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.LPAREN);

        List<ParamNode> params = new ArrayList<>();

        // Optional parameter list
        if (!check(TokenType.RPAREN)) {
            params.add(parseParam());
            while (check(TokenType.COMMA)) {
                advance(); // consume ","
                params.add(parseParam());
            }
        }

        expect(TokenType.RPAREN);
        expect(TokenType.ARROW_RETURN); // ->
        String returnType = parseReturnType();
        BlockNode body = parseBlock();

        return new FuncDeclNode(nameTok.value, params, returnType, body, line);
    }

    /**
     * Helper: parse one function parameter.
     * <param> ::= IDENT ":" <param_type>
     * <param_type> ::= "int" | "float" | "bool" | "string" | "semaphore" | "process"
     */
    private ParamNode parseParam() {
        int line = peek().line;
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.COLON);
        String type = parseParamType();
        return new ParamNode(nameTok.value, type, line);
    }

    /**
     * Helper: consume and return a param type keyword as a string.
     * Valid: int, float, bool, string, semaphore, process
     */
    private String parseParamType() {
        Token t = peek();
        switch (t.type) {
            case KW_INT:       advance(); return "int";
            case KW_FLOAT:     advance(); return "float";
            case KW_BOOL:      advance(); return "bool";
            case KW_STRING:    advance(); return "string";
            case KW_SEMAPHORE: advance(); return "semaphore";
            case KW_PROCESS:   advance(); return "process";
            default:
                throw new RuntimeException(
                    "[Parser Error] Line " + t.line +
                    ": Expected a parameter type (int, float, bool, string, semaphore, process)" +
                    " but found '" + t.value + "'");
        }
    }

    /**
     * Helper: consume and return a return type keyword as a string.
     * Valid: int, float, bool, string  (semaphore and process NOT allowed as return types)
     */
    private String parseReturnType() {
        Token t = peek();
        switch (t.type) {
            case KW_INT:    advance(); return "int";
            case KW_FLOAT:  advance(); return "float";
            case KW_BOOL:   advance(); return "bool";
            case KW_STRING: advance(); return "string";
            default:
                throw new RuntimeException(
                    "[Parser Error] Line " + t.line +
                    ": Expected a return type (int, float, bool, string)" +
                    " but found '" + t.value + "'");
        }
    }

    /**
     * Helper: consume and return a decl type as a string.
     * <decl_type> ::= <simple_type> | IDENT
     * Used by static declarations and var decl statements.
     */
    private String parseDeclType() {
        Token t = peek();
        switch (t.type) {
            case KW_INT:    advance(); return "int";
            case KW_FLOAT:  advance(); return "float";
            case KW_BOOL:   advance(); return "bool";
            case KW_STRING: advance(); return "string";
            case IDENT:     advance(); return t.value; // enum type name
            default:
                throw new RuntimeException(
                    "[Parser Error] Line " + t.line +
                    ": Expected a type name but found '" + t.value + "'");
        }
    }

    /**
     * system IDENT ( processes: [ <process_list> ] , scheduler: <scheduler> ) ;
     * Fixed field order — processes first, then scheduler (§5.18).
     * At least one process required — empty list is a parse error.
     */
    private ASTNode parseSystemDecl() {
        int line = peek().line;
        expect(TokenType.KW_SYSTEM);
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.LPAREN);

        // processes: [ P1, P2, ... ]
        expect(TokenType.KW_PROCESSES);
        expect(TokenType.COLON);
        expect(TokenType.LBRACKET);

        List<String> processes = new ArrayList<>();
        Token first = peek();
        if (first.type == TokenType.RBRACKET) {
            throw new RuntimeException(
                "[Parser Error] Line " + first.line +
                ": System '" + nameTok.value + "' must have at least one process");
        }
        processes.add(expect(TokenType.IDENT).value);
        while (check(TokenType.COMMA)) {
            advance();
            processes.add(expect(TokenType.IDENT).value);
        }
        expect(TokenType.RBRACKET);

        expect(TokenType.COMMA);

        // scheduler: <scheduler>
        expect(TokenType.KW_SCHEDULER);
        expect(TokenType.COLON);
        SchedulerNode scheduler = parseScheduler();

        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);

        return new SystemDeclNode(nameTok.value, processes, scheduler, line);
    }

    /**
     * Helper: parse a scheduler clause.
     * <scheduler> ::= "FCFS" | "SJF" | "SRTF" | "PRIORITY"
     *               | "RR" "(" "quant" ":" INT_LIT ")"
     * quant is INT_LIT only — not a general expression (§5.19).
     */
    private SchedulerNode parseScheduler() {
        int line = peek().line;
        Token t = peek();

        switch (t.type) {
            case KW_FCFS:     advance(); return new SchedulerNode("FCFS",     null, line);
            case KW_SJF:      advance(); return new SchedulerNode("SJF",      null, line);
            case KW_SRTF:     advance(); return new SchedulerNode("SRTF",     null, line);
            case KW_PRIORITY: advance(); return new SchedulerNode("PRIORITY", null, line);
            case KW_RR: {
                advance(); // consume "RR"
                expect(TokenType.LPAREN);
                expect(TokenType.KW_QUANT);
                expect(TokenType.COLON);
                Token quantTok = expect(TokenType.INT_LIT);
                int quantVal = Integer.parseInt(quantTok.value);
                if (quantVal <= 0) {
                    throw new RuntimeException(
                        "[Parser Error] Line " + quantTok.line +
                        ": RR quant must be a positive integer, got " + quantVal);
                }
                expect(TokenType.RPAREN);
                return new SchedulerNode("RR", new IntLitNode(quantVal, line), line);
            }
            default:
                throw new RuntimeException(
                    "[Parser Error] Line " + t.line +
                    ": Expected a scheduler (FCFS, SJF, SRTF, PRIORITY, RR)" +
                    " but found '" + t.value + "'");
        }
    }

    /**
     * IDENT . add ( IDENT , arrival : <expr> ) ;
     */
    private ASTNode parseAddStmt() {
        int line = peek().line;
        Token sysNameTok = expect(TokenType.IDENT);
        expect(TokenType.DOT);
        expect(TokenType.KW_ADD);
        expect(TokenType.LPAREN);
        Token procNameTok = expect(TokenType.IDENT);
        expect(TokenType.COMMA);
        expect(TokenType.KW_ARRIVAL);
        expect(TokenType.COLON);
        ASTNode arrival = parseExpr();
        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);

        return new AddStmtNode(sysNameTok.value, procNameTok.value, arrival, line);
    }

    /**
     * run ( IDENT [ , until : <expr> ] ) ;
     * until clause is optional — null in RunStmtNode when absent.
     */
    private ASTNode parseRunStmt() {
        int line = peek().line;
        expect(TokenType.KW_RUN);
        expect(TokenType.LPAREN);
        Token sysNameTok = expect(TokenType.IDENT);

        ASTNode until = null;
        if (check(TokenType.COMMA)) {
            advance(); // consume ","
            expect(TokenType.KW_UNTIL);
            expect(TokenType.COLON);
            until = parseExpr();
        }

        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);

        return new RunStmtNode(sysNameTok.value, until, line);
    }

    // -------------------------------------------------------------------------
    // Statement parsers — Step 4
    // -------------------------------------------------------------------------

    /**
     * <block> ::= "{" { <statement> } "}"
     * Zero or more statements — empty body is allowed (§5.15).
     */
    private BlockNode parseBlock() {
        int line = peek().line;
        expect(TokenType.LBRACE);
        List<ASTNode> stmts = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !check(TokenType.EOF)) {
            stmts.add(parseStatement());
        }
        expect(TokenType.RBRACE);
        return new BlockNode(stmts, line);
    }

    /**
     * Dispatch to the correct statement parser.
     *
     * LL(2) disambiguation (§5.26):
     *   Simple type kw         → var decl  (int/float/bool/string)
     *   IDENT + IDENT          → var decl  (enum-typed: State s <- ...)
     *   IDENT + ARROW          → assign    (x <- ...)
     *   IDENT + LPAREN         → call      (foo(...))
     *   KW_WAIT/POST/PRINT     → call      (built-in call)
     *   KW_IF                  → if stmt
     *   KW_WHILE               → while stmt
     *   KW_RETURN              → return stmt
     */
    private ASTNode parseStatement() {
        TokenType t  = peek().type;
        TokenType t2 = peek2().type;

        // Simple type keyword → var decl
        if (t == TokenType.KW_INT   || t == TokenType.KW_FLOAT ||
            t == TokenType.KW_BOOL  || t == TokenType.KW_STRING) {
            return parseVarDeclStmt();
        }

        // IDENT IDENT → enum-typed var decl  (e.g. State s <- ready;)
        if (t == TokenType.IDENT && t2 == TokenType.IDENT) {
            return parseVarDeclStmt();
        }

        // IDENT <- → assignment
        if (t == TokenType.IDENT && t2 == TokenType.ARROW) {
            return parseAssignStmt();
        }

        // IDENT ( → user-defined function call
        if (t == TokenType.IDENT && t2 == TokenType.LPAREN) {
            return parseCallStmt();
        }

        // Built-in calls — lexer emits keyword tokens, not IDENT (§5.25 Decision 1)
        if (t == TokenType.KW_WAIT || t == TokenType.KW_POST || t == TokenType.KW_PRINT) {
            return parseCallStmt();
        }

        if (t == TokenType.KW_IF)     return parseIfStmt();
        if (t == TokenType.KW_WHILE)  return parseWhileStmt();
        if (t == TokenType.KW_RETURN) return parseReturnStmt();

        Token bad = peek();
        throw new RuntimeException(
            "[Parser Error] Line " + bad.line +
            ": Unexpected token '" + bad.value + "' in statement");
    }

    /**
     * <var_decl_stmt> ::= <decl_type> IDENT "<-" <expr> ";"
     * Initializer is mandatory — bare declarations not allowed (§5.26,
     * Sebesta §5.4.2 reliability argument).
     */
    private ASTNode parseVarDeclStmt() {
        int line = peek().line;
        String declType = parseDeclType();
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.ARROW);
        ASTNode init = parseExpr();
        expect(TokenType.SEMICOLON);
        return new VarDeclStmtNode(declType, nameTok.value, init, line);
    }

    /**
     * <assign_stmt> ::= IDENT "<-" <expr> ";"
     * Assignment is statement only — never an expression (§5.14,
     * Sebesta §7.7 — eliminates accidental assignment-in-condition bugs).
     */
    private ASTNode parseAssignStmt() {
        int line = peek().line;
        Token nameTok = expect(TokenType.IDENT);
        expect(TokenType.ARROW);
        ASTNode value = parseExpr();
        expect(TokenType.SEMICOLON);
        return new AssignStmtNode(nameTok.value, value, line);
    }

    /**
     * <call_stmt> ::= (IDENT | "wait" | "post" | "print") "(" [ <arg_list> ] ")" ";"
     * wait/post/print arrive as KW_WAIT/KW_POST/KW_PRINT tokens — their string
     * value is stored in the node so the interpreter can dispatch on it (§5.25 Decision 1).
     */
    private ASTNode parseCallStmt() {
        int line = peek().line;
        Token nameTok = peek();

        // Accept IDENT or any built-in keyword as the function name
        if (nameTok.type == TokenType.IDENT       ||
            nameTok.type == TokenType.KW_WAIT     ||
            nameTok.type == TokenType.KW_POST     ||
            nameTok.type == TokenType.KW_PRINT) {
            advance();
        } else {
            throw new RuntimeException(
                "[Parser Error] Line " + nameTok.line +
                ": Expected a function name but found '" + nameTok.value + "'");
        }

        expect(TokenType.LPAREN);
        List<ASTNode> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parseExpr());
            while (check(TokenType.COMMA)) {
                advance();
                args.add(parseExpr());
            }
        }
        expect(TokenType.RPAREN);
        expect(TokenType.SEMICOLON);

        return new CallStmtNode(nameTok.value, args, line);
    }

    /**
     * <return_stmt> ::= "return" <expr> ";"
     * Bare return; is not allowed — every function has a non-void return type (§5.21).
     */
    private ASTNode parseReturnStmt() {
        int line = peek().line;
        expect(TokenType.KW_RETURN);
        ASTNode value = parseExpr();
        expect(TokenType.SEMICOLON);
        return new ReturnStmtNode(value, line);
    }

    /**
     * <while_stmt> ::= "while" "(" <expr> ")" <block>
     * Parentheses around condition are mandatory (§5.23).
     */
    private ASTNode parseWhileStmt() {
        int line = peek().line;
        expect(TokenType.KW_WHILE);
        expect(TokenType.LPAREN);
        ASTNode condition = parseExpr();
        expect(TokenType.RPAREN);
        BlockNode body = parseBlock();
        return new WhileStmtNode(condition, body, line);
    }

    /**
     * <if_stmt> ::= "if" "(" <expr> ")" <block>
     *               { "elif" "(" <expr> ")" <block> }
     *               [ "else" <block> ]
     *
     * Dangling-else cannot arise — all bodies are mandatory blocks (§5.26,
     * Sebesta §3.3.1.4). elif is a single keyword token, not else+if (§5.25).
     * Parentheses around conditions are mandatory (§5.23).
     */
    private ASTNode parseIfStmt() {
        int line = peek().line;
        expect(TokenType.KW_IF);
        expect(TokenType.LPAREN);
        ASTNode condition = parseExpr();
        expect(TokenType.RPAREN);
        BlockNode thenBlock = parseBlock();

        // Zero or more elif clauses
        List<ElifClauseNode> elifClauses = new ArrayList<>();
        while (check(TokenType.KW_ELIF)) {
            int elifLine = peek().line;
            advance(); // consume "elif"
            expect(TokenType.LPAREN);
            ASTNode elifCond = parseExpr();
            expect(TokenType.RPAREN);
            BlockNode elifBody = parseBlock();
            elifClauses.add(new ElifClauseNode(elifCond, elifBody, elifLine));
        }

        // Optional else clause
        BlockNode elseBlock = null;
        if (check(TokenType.KW_ELSE)) {
            advance(); // consume "else"
            elseBlock = parseBlock();
        }

        return new IfStmtNode(condition, thenBlock, elifClauses, elseBlock, line);
    }

    // -------------------------------------------------------------------------
    // Expression parsers — Step 5
    //
    // Precedence cascade (bottom-up, lowest first) — Sebesta §7.3, §5.12:
    //   parseExpr            → or
    //   parseOrExpr          → and  { "||" and }
    //   parseAndExpr         → eq   { "&&" eq }
    //   parseEqualityExpr    → rel  { ("==" | "!=") rel }
    //   parseRelationalExpr  → add  { ("<"|">"|"<="|">=") add }
    //   parseAdditiveExpr    → mul  { ("+"|"-") mul }
    //   parseMultiplicativeExpr → unary { ("*"|"/"|"%") unary }
    //   parseUnaryExpr       → ("!" | "-") unary  |  postfix   (right-recursive)
    //   parsePostfixExpr     → primary [ "." attr ]
    //   parsePrimary         → literal | IDENT ["(" args ")"] | "(" expr ")"
    //
    // Left-associativity: each binary level uses a while loop that builds the
    // tree left-to-right: left = new BinOpNode(op, left, right).
    // Sebesta §3.3.2 p.127: EBNF { } does NOT encode associativity — the
    // parser loop is what enforces it (§5.13 decision).
    // -------------------------------------------------------------------------

    /** <expr> ::= <or_expr>  — top of the precedence cascade. */
    private ASTNode parseExpr() {
        return parseOrExpr();
    }

    /**
     * <or_expr> ::= <and_expr> { "||" <and_expr> }
     * Lowest precedence binary operator — level 7 (§5.12).
     */
    private ASTNode parseOrExpr() {
        int line = peek().line;
        ASTNode left = parseAndExpr();
        while (check(TokenType.OR)) {
            advance();
            ASTNode right = parseAndExpr();
            left = new BinOpNode("||", left, right, line);
            line = peek().line;
        }
        return left;
    }

    /**
     * <and_expr> ::= <equality_expr> { "&&" <equality_expr> }
     * Level 6.
     */
    private ASTNode parseAndExpr() {
        int line = peek().line;
        ASTNode left = parseEqualityExpr();
        while (check(TokenType.AND)) {
            advance();
            ASTNode right = parseEqualityExpr();
            left = new BinOpNode("&&", left, right, line);
            line = peek().line;
        }
        return left;
    }

    /**
     * <equality_expr> ::= <relational_expr> { ("==" | "!=") <relational_expr> }
     * Level 5.
     */
    private ASTNode parseEqualityExpr() {
        int line = peek().line;
        ASTNode left = parseRelationalExpr();
        while (check(TokenType.EQ) || check(TokenType.NEQ)) {
            String op = advance().value;
            ASTNode right = parseRelationalExpr();
            left = new BinOpNode(op, left, right, line);
            line = peek().line;
        }
        return left;
    }

    /**
     * <relational_expr> ::= <additive_expr> { ("<"|">"|"<="|">=") <additive_expr> }
     * Level 4.
     */
    private ASTNode parseRelationalExpr() {
        int line = peek().line;
        ASTNode left = parseAdditiveExpr();
        while (check(TokenType.LT)  || check(TokenType.GT) ||
               check(TokenType.LEQ) || check(TokenType.GEQ)) {
            String op = advance().value;
            ASTNode right = parseAdditiveExpr();
            left = new BinOpNode(op, left, right, line);
            line = peek().line;
        }
        return left;
    }

    /**
     * <additive_expr> ::= <multiplicative_expr> { ("+"|"-") <multiplicative_expr> }
     * Level 3.
     */
    private ASTNode parseAdditiveExpr() {
        int line = peek().line;
        ASTNode left = parseMultiplicativeExpr();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            String op = advance().value;
            ASTNode right = parseMultiplicativeExpr();
            left = new BinOpNode(op, left, right, line);
            line = peek().line;
        }
        return left;
    }

    /**
     * <multiplicative_expr> ::= <unary_expr> { ("*"|"/"|"%") <unary_expr> }
     * Level 2.
     */
    private ASTNode parseMultiplicativeExpr() {
        int line = peek().line;
        ASTNode left = parseUnaryExpr();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            String op = advance().value;
            ASTNode right = parseUnaryExpr();
            left = new BinOpNode(op, left, right, line);
            line = peek().line;
        }
        return left;
    }

    /**
     * <unary_expr> ::= ("!" | "-") <unary_expr>  |  <postfix_expr>
     * Level 1 (highest). Right-associative — method recurses on itself.
     * !!x parses as !(!x), -(-x) parses as -(-(x)).
     */
    private ASTNode parseUnaryExpr() {
        int line = peek().line;
        if (check(TokenType.NOT)) {
            advance();
            return new UnaryOpNode("!", parseUnaryExpr(), line);
        }
        if (check(TokenType.MINUS)) {
            advance();
            return new UnaryOpNode("-", parseUnaryExpr(), line);
        }
        return parsePostfixExpr();
    }

    /**
     * <postfix_expr> ::= <primary> [ "." <process_attr> ]
     * <process_attr> ::= "state" | "burst" | "priority" | "arrival"
     *
     * One level deep only — a.b.c does not parse (§5.26).
     * Closed attribute set — anything else after "." is a parse error.
     * Note: "priority" arrives as KW_PRIORITY_F (§5.25 Decision 2).
     */
    private ASTNode parsePostfixExpr() {
        int line = peek().line;
        ASTNode obj = parsePrimary();

        if (check(TokenType.DOT)) {
            advance(); // consume "."
            Token attr = peek();
            String attrName;
            if      (attr.type == TokenType.KW_STATE)      { attrName = "state";    }
            else if (attr.type == TokenType.KW_BURST)      { attrName = "burst";    }
            else if (attr.type == TokenType.KW_PRIORITY_F) { attrName = "priority"; }
            else if (attr.type == TokenType.KW_ARRIVAL)    { attrName = "arrival";  }
            else {
                throw new RuntimeException(
                    "[Parser Error] Line " + attr.line +
                    ": Unknown process attribute '" + attr.value +
                    "'. Expected 'state', 'burst', 'priority', or 'arrival'");
            }
            advance();
            return new PostfixDotNode(obj, attrName, line);
        }

        return obj;
    }

    /**
     * <primary> ::= <literal>
     *             | IDENT [ "(" [ <arg_list> ] ")" ]
     *             | "(" <expr> ")"
     *
     * Bare IDENT     → IdentNode   (variable or enum member reference)
     * IDENT "(" ...) → FuncCallNode (function call inside an expression)
     * "(" <expr> ")" → grouped expression
     */
    private ASTNode parsePrimary() {
        int line = peek().line;
        Token t = peek();

        // Integer literal
        if (t.type == TokenType.INT_LIT) {
            advance();
            return new IntLitNode(Integer.parseInt(t.value), line);
        }

        // Float literal
        if (t.type == TokenType.FLOAT_LIT) {
            advance();
            return new FloatLitNode(Double.parseDouble(t.value), line);
        }

        // Boolean literal — lexer emits BOOL_LIT for true/false (§5.25 Decision 3)
        if (t.type == TokenType.BOOL_LIT) {
            advance();
            return new BoolLitNode(t.value.equals("true"), line);
        }

        // String literal
        if (t.type == TokenType.STRING_LIT) {
            advance();
            return new StringLitNode(t.value, line);
        }

        // IDENT — variable reference or function call
        if (t.type == TokenType.IDENT) {
            advance();
            if (check(TokenType.LPAREN)) {
                // Function call expression: IDENT ( [ args ] )
                advance(); // consume "("
                List<ASTNode> args = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    args.add(parseExpr());
                    while (check(TokenType.COMMA)) {
                        advance();
                        args.add(parseExpr());
                    }
                }
                expect(TokenType.RPAREN);
                return new FuncCallNode(t.value, args, line);
            }
            // Bare identifier — variable or enum member
            return new IdentNode(t.value, line);
        }

        // Grouped expression: "(" <expr> ")"
        if (t.type == TokenType.LPAREN) {
            advance(); // consume "("
            ASTNode inner = parseExpr();
            expect(TokenType.RPAREN);
            return inner;
        }

        throw new RuntimeException(
            "[Parser Error] Line " + t.line +
            ": Unexpected token '" + t.value + "' in expression");
    }

    // -------------------------------------------------------------------------
    // Argument list helper — shared by parsePrimary (FuncCallNode)
    // -------------------------------------------------------------------------

    /**
     * <arg_list> ::= <expr> { "," <expr> }
     * Called when we already know at least one argument exists.
     */
    private List<ASTNode> parseArgList() {
        List<ASTNode> args = new ArrayList<>();
        args.add(parseExpr());
        while (check(TokenType.COMMA)) {
            advance();
            args.add(parseExpr());
        }
        return args;
    }
}