# OSlang — Lexer & Parser Implementation Route

> **CSE 341 · Gebze Technical University · Spring 2026**
> This document is the step-by-step implementation plan for the Part 1 lexer and parser.
> The grammar it implements is the fully locked grammar from README §5.24 (Round 6, all 4 review issues resolved).
> Read this alongside the README. Do not implement anything that contradicts the README — the README is the single source of truth for every design decision.

---

## Who Owns What

The split follows the natural boundary between the two components. This boundary matters beyond fairness — the in-class exam on 14 May targets each partner on the components they personally wrote. Whatever you own, you must be able to trace through it on paper and cite the Sebesta section behind every decision.

| Component | Files | Owner |
|---|---|---|
| Token type enum | `TokenType.java` | **Partner A** |
| Token class | `Token.java` | **Partner A** |
| Lexer | `Lexer.java` | **Partner A** |
| AST node classes | `AST.java` (or one file per node) | **Partner B** |
| Parser | `Parser.java` | **Partner B** |
| Main entry point | `Main.java` | Either — agree once |
| D1 §4.2 Lexical Structure | Written section | **Partner A** |
| D1 §4.3 Syntax (EBNF) | Written section | **Partner B** |
| D1 §4.1 and §4.6 | Written sections | Both — write together |
| D3 malformed programs (lexer errors) | 2–3 programs | **Partner A** |
| D3 malformed programs (parser errors) | 2–3 programs | **Partner B** |
| D3 valid programs | 3 programs | Split evenly |

---

## Step 0 — Agree the Interface First (Both Partners, ~15 min)

Before either partner writes a single line of implementation, agree on `TokenType` and `Token`. This is the only shared interface between the two components. Once it is locked, both partners can work fully in parallel.

### `TokenType.java`

```java
public enum TokenType {
    // --- Literals ---
    INT_LIT,        // e.g. 42
    FLOAT_LIT,      // e.g. 3.14
    BOOL_LIT,       // true or false — emitted by the lexer, NOT a grammar keyword
    STRING_LIT,     // e.g. "hello"

    // --- Identifier ---
    IDENT,          // any user-defined name

    // --- Type keywords ---
    KW_INT, KW_FLOAT, KW_BOOL, KW_STRING,
    KW_SEMAPHORE, KW_PROCESS, KW_SYSTEM,
    KW_ENUM, KW_STATIC, KW_FUNC,

    // --- Control flow keywords ---
    KW_IF, KW_ELIF, KW_ELSE, KW_WHILE, KW_RETURN,

    // --- Top-level operation keywords ---
    KW_RUN, KW_ADD,

    // --- Scheduler keywords (uppercase) ---
    KW_FCFS, KW_SJF, KW_SRTF, KW_RR, KW_PRIORITY,

    // --- Field / attribute keywords ---
    KW_PROCESSES, KW_SCHEDULER, KW_QUANT,
    KW_BURST, KW_PRIORITY_F,   // priority (field/lowercase) vs PRIORITY (scheduler/uppercase)
    KW_ARRIVAL, KW_UNTIL, KW_STATE,

    // --- Assignment operator ---
    ARROW,          // <-

    // --- Comparison operators ---
    EQ, NEQ, LT, GT, LEQ, GEQ,

    // --- Boolean operators ---
    AND, OR, NOT,   // && || !

    // --- Arithmetic operators ---
    PLUS, MINUS, STAR, SLASH, PERCENT,

    // --- Function return arrow ---
    ARROW_RETURN,   // ->

    // --- Separators ---
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, SEMICOLON, COLON, DOT,

    // --- Special ---
    EOF
}
```

### `Token.java`

```java
public class Token {
    public final TokenType type;
    public final String value;  // raw lexeme as it appeared in source
    public final int line;      // 1-based line number, for error messages

    public Token(TokenType type, String value, int line) {
        this.type  = type;
        this.value = value;
        this.line  = line;
    }

    @Override
    public String toString() {
        return String.format("Token(%s, \"%s\", line=%d)", type, value, line);
    }
}
```

**Note on `priority`:** The lexeme `priority` (lowercase) is a process field name; `PRIORITY` (uppercase) is a scheduler name. The lexer emits `KW_PRIORITY_F` for lowercase and `KW_PRIORITY` for uppercase. If you prefer simplicity, use a single token for both and resolve by context in the parser — agree before starting.

---

## Part A — Lexer

**Owner: Partner A**

The lexer reads source characters and produces a flat list of `Token` objects. The parser consumes that list. They communicate only through `Token`.

### A1 — The Keyword Table

Build this table first. The lexer reads characters to form a candidate identifier, then checks here. A match emits the keyword token instead of `IDENT`.

```java
private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
static {
    KEYWORDS.put("int",        TokenType.KW_INT);
    KEYWORDS.put("float",      TokenType.KW_FLOAT);
    KEYWORDS.put("bool",       TokenType.KW_BOOL);
    KEYWORDS.put("string",     TokenType.KW_STRING);
    KEYWORDS.put("semaphore",  TokenType.KW_SEMAPHORE);
    KEYWORDS.put("process",    TokenType.KW_PROCESS);
    KEYWORDS.put("system",     TokenType.KW_SYSTEM);
    KEYWORDS.put("enum",       TokenType.KW_ENUM);
    KEYWORDS.put("static",     TokenType.KW_STATIC);
    KEYWORDS.put("func",       TokenType.KW_FUNC);
    KEYWORDS.put("if",         TokenType.KW_IF);
    KEYWORDS.put("elif",       TokenType.KW_ELIF);
    KEYWORDS.put("else",       TokenType.KW_ELSE);
    KEYWORDS.put("while",      TokenType.KW_WHILE);
    KEYWORDS.put("return",     TokenType.KW_RETURN);
    KEYWORDS.put("run",        TokenType.KW_RUN);
    KEYWORDS.put("add",        TokenType.KW_ADD);
    KEYWORDS.put("FCFS",       TokenType.KW_FCFS);
    KEYWORDS.put("SJF",        TokenType.KW_SJF);
    KEYWORDS.put("SRTF",       TokenType.KW_SRTF);
    KEYWORDS.put("RR",         TokenType.KW_RR);
    KEYWORDS.put("PRIORITY",   TokenType.KW_PRIORITY);
    KEYWORDS.put("processes",  TokenType.KW_PROCESSES);
    KEYWORDS.put("scheduler",  TokenType.KW_SCHEDULER);
    KEYWORDS.put("quant",      TokenType.KW_QUANT);
    KEYWORDS.put("burst",      TokenType.KW_BURST);
    KEYWORDS.put("priority",   TokenType.KW_PRIORITY_F);
    KEYWORDS.put("arrival",    TokenType.KW_ARRIVAL);
    KEYWORDS.put("until",      TokenType.KW_UNTIL);
    KEYWORDS.put("state",      TokenType.KW_STATE);
    // Boolean literals enter through the keyword table
    // They emit BOOL_LIT, not a grammar keyword — README §5.24 Step 6 Issue 4
    KEYWORDS.put("true",       TokenType.BOOL_LIT);
    KEYWORDS.put("false",      TokenType.BOOL_LIT);
}
```

**Sebesta §4.2:** Keywords are recognized by checking candidate identifiers against this table after the identifier-reading state completes. `true` and `false` follow the same path — they produce `BOOL_LIT` rather than `IDENT`, consistent with how every other literal type is handled.

### A2 — The Scan Loop

Implement `nextToken()` (or `tokenize()` returning the full list). Work through these cases in order — each one adds a new branch:

**A2.1 — Whitespace and newlines**

Skip spaces, tabs, and `\r`. Increment `line` counter on `\n`. No token emitted.

**A2.2 — Comments (`//`)**

On `/`, peek next char. If also `/`, advance until `\n` or EOF — no token. If not `/`, emit `SLASH`. No block comments in OSlang (README §5.24 Step 6 Issue 3).

**A2.3 — IDENT and keywords**

On a letter or `_`, accumulate while the character is a letter, digit, or `_`. Look up in the keyword table. Emit keyword token if found, `IDENT` otherwise.

```
Pattern: [a-zA-Z_][a-zA-Z0-9_]*
```

**A2.4 — INT_LIT and FLOAT_LIT**

On a digit, accumulate digits. After the run, peek: if next is `.` AND the char after that is also a digit, consume `.`, accumulate fractional digits, emit `FLOAT_LIT`. Otherwise emit `INT_LIT`.

```
INT_LIT   pattern: [0-9]+
FLOAT_LIT pattern: [0-9]+ '.' [0-9]+
```

`.5` and `5.` are NOT valid — both sides of the dot require at least one digit (README §5.24 Step 3). This prevents ambiguity with the `.` in `p.state`.

**A2.5 — STRING_LIT**

On `"`, accumulate until closing `"`. Handle escape sequences: `\"` `\\` `\n` `\t`. A newline before the closing `"` is a lexer error — multi-line strings are not supported (README §5.24 Step 6 Issue 2).

```
Pattern: '"' { any_char_except_quote_and_newline | '\' ( '"' | '\' | 'n' | 't' ) } '"'
```

**A2.6 — Operators (maximal munch)**

Always consume the longest valid token. The ambiguous cases where the first character alone is not enough:

| First char | Peek next | Emit |
|---|---|---|
| `<` | `-` | `ARROW` (`<-`) |
| `<` | `=` | `LEQ` (`<=`) |
| `<` | else | `LT` (`<`) |
| `-` | `>` | `ARROW_RETURN` (`->`) |
| `-` | else | `MINUS` (`-`) |
| `=` | `=` | `EQ` (`==`) |
| `!` | `=` | `NEQ` (`!=`) |
| `!` | else | `NOT` (`!`) |
| `&` | `&` | `AND` (`&&`) |
| `\|` | `\|` | `OR` (`\|\|`) |

Single-character tokens (`+`, `*`, `/`, `%`, `>`, `(`, `)`, `{`, `}`, `[`, `]`, `,`, `;`, `:`, `.`) need no peeking.

**A2.7 — Unknown characters**

Any unmatched character is a lexer error. Emit a message with line number and character. Halt on first error for Part 1.

### A3 — Error Message Format

```
[Lexer Error] Line 7: Unexpected character '@'
[Lexer Error] Line 12: Unterminated string literal
[Lexer Error] Line 3: Invalid escape sequence '\q' in string literal
```

### A4 — Test the Lexer Standalone

Use `--dump-tokens` to verify the output by hand before the parser is ready:

| Input | Expected tokens |
|---|---|
| `static int maxTick <- 100;` | `KW_STATIC` `KW_INT` `IDENT(maxTick)` `ARROW` `INT_LIT(100)` `SEMICOLON` |
| `print("hello\nworld");` | `IDENT(print)` `LPAREN` `STRING_LIT(hello\nworld)` `RPAREN` `SEMICOLON` |
| `x <- y <= z < 5` | `IDENT(x)` `ARROW` `IDENT(y)` `LEQ` `IDENT(z)` `LT` `INT_LIT(5)` |
| `// init\nsemaphore mutex <- 1;` | `KW_SEMAPHORE` `IDENT(mutex)` `ARROW` `INT_LIT(1)` `SEMICOLON` |

---

## Part B — Parser

**Owner: Partner B**

The parser takes the token list and builds an AST. It is a **recursive-descent parser** — one method per non-terminal in the EBNF grammar. The code structure mirrors the grammar exactly. This is the direct application of Sebesta §4.4.

### B1 — The Parser Skeleton

```java
public class Parser {
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) { this.tokens = tokens; }

    private Token peek()  { return tokens.get(pos); }

    // Used for LL(2) disambiguation — see B3
    private Token peek2() {
        int next = pos + 1;
        return next < tokens.size() ? tokens.get(next) : tokens.get(tokens.size() - 1);
    }

    private Token advance() {
        Token t = tokens.get(pos);
        if (pos < tokens.size() - 1) pos++;
        return t;
    }

    private Token expect(TokenType type) {
        Token t = peek();
        if (t.type != type) throw new ParseError(String.format(
            "[Parser Error] Line %d: Expected %s but found '%s'", t.line, type, t.value));
        return advance();
    }

    private boolean check(TokenType type) { return peek().type == type; }
}
```

### B2 — AST Node Classes

Define all nodes before writing parsing methods. Every node needs a `dump(int indent)` method for the `--dump-ast` output required by the handout.

```java
// Top-level
class ProgramNode       { List<ASTNode> declarations; }
class StaticDeclNode    { String declType; String name; ASTNode init; }
class SemaphoreDeclNode { String name; ASTNode init; }
class EnumDeclNode      { String name; List<String> members; }
class ProcessDeclNode   { String name; List<ProcessFieldNode> fields; BlockNode body; }
class FuncDeclNode      { String name; List<ParamNode> params;
                          String returnType; BlockNode body; }
class SystemDeclNode    { String name; List<String> processes; SchedulerNode scheduler; }
class AddStmtNode       { String systemName; String processName; ASTNode arrival; }
class RunStmtNode       { String systemName; ASTNode until; /* null if omitted */ }

// Helpers
class ProcessFieldNode  { String fieldName; ASTNode value; }
class ParamNode         { String name; String type; }
class SchedulerNode     { String name; ASTNode quant; /* null unless RR */ }

// Statements
class BlockNode         { List<ASTNode> statements; }
class VarDeclStmtNode   { String declType; String name; ASTNode init; }
class AssignStmtNode    { String target; ASTNode value; }
class CallStmtNode      { String name; List<ASTNode> args; }
class ReturnStmtNode    { ASTNode value; }
class WhileStmtNode     { ASTNode condition; BlockNode body; }
class IfStmtNode        { ASTNode condition; BlockNode thenBlock;
                          List<ElifClauseNode> elifClauses; BlockNode elseBlock; }
class ElifClauseNode    { ASTNode condition; BlockNode body; }

// Expressions
class BinOpNode         { String op; ASTNode left; ASTNode right; }
class UnaryOpNode       { String op; ASTNode operand; }
class PostfixDotNode    { ASTNode object; String attribute; }
class FuncCallNode      { String name; List<ASTNode> args; }
class IdentNode         { String name; }
class IntLitNode        { int value; }
class FloatLitNode      { double value; }
class BoolLitNode       { boolean value; }
class StringLitNode     { String value; }
```

### B3 — The LL(2) Disambiguation Point

This is the most important parser detail. Two statement forms both begin with `IDENT`:

```
<assign_stmt>   ::= IDENT "<-" <expr> ";"      peek()=IDENT, peek2()=ARROW
<call_stmt>     ::= IDENT "(" ... ")" ";"      peek()=IDENT, peek2()=LPAREN
<var_decl_stmt> (enum type) ::= IDENT IDENT... peek()=IDENT, peek2()=IDENT
```

The full dispatch for `parseStatement()`:

```java
private ASTNode parseStatement() {
    TokenType t  = peek().type;
    TokenType t2 = peek2().type;

    // Definite type keyword → var decl
    if (t == KW_INT || t == KW_FLOAT || t == KW_BOOL || t == KW_STRING)
        return parseVarDeclStmt();
    // IDENT followed by another IDENT → enum-typed var decl
    if (t == IDENT && t2 == IDENT)
        return parseVarDeclStmt();
    // IDENT followed by <- → assignment
    if (t == IDENT && t2 == ARROW)
        return parseAssignStmt();
    // IDENT followed by ( → call as statement
    if (t == IDENT && t2 == LPAREN)
        return parseCallStmt();

    if (t == KW_IF)     return parseIfStmt();
    if (t == KW_WHILE)  return parseWhileStmt();
    if (t == KW_RETURN) return parseReturnStmt();

    Token tok = peek();
    throw new ParseError(String.format(
        "[Parser Error] Line %d: Unexpected token '%s' in statement", tok.line, tok.value));
}
```

Document in D1 §4.3: *"The grammar is LL(2) at the statement level. The parser peeks one token ahead to distinguish assignment from call-as-statement, and to distinguish enum-typed variable declarations from other statement forms."* This is the direct application of Sebesta §4.4.1.

### B4 — Top-Level Parsing

Start here. Stub all called methods as `throw new RuntimeException("TODO")` and fill in one by one.

```java
public ProgramNode parseProgram() {
    List<ASTNode> decls = new ArrayList<>();
    while (!check(EOF)) decls.add(parseTopLevelDecl());
    if (decls.isEmpty()) throw new ParseError(
        "[Parser Error] Line 1: Empty program — at least one declaration is required");
    return new ProgramNode(decls);
}

private ASTNode parseTopLevelDecl() {
    switch (peek().type) {
        case KW_STATIC:    return parseStaticDecl();
        case KW_SEMAPHORE: return parseSemaphoreDecl();
        case KW_ENUM:      return parseEnumDecl();
        case KW_PROCESS:   return parseProcessDecl();
        case KW_FUNC:      return parseFuncDecl();
        case KW_SYSTEM:    return parseSystemDecl();
        case KW_RUN:       return parseRunStmt();
        case IDENT:
            if (peek2().type == DOT) return parseAddStmt(); // IDENT "." "add" (...)
        default:
            Token t = peek();
            throw new ParseError(String.format(
                "[Parser Error] Line %d: Unexpected token '%s' at top level", t.line, t.value));
    }
}
```

### B5 — Declarations

Translate each grammar rule from README §5.24 Steps 2 and 5 directly. The pattern is always: `expect()` terminals, recurse for non-terminals, return the node.

**`parseStaticDecl()`** — grammar: `"static" <decl_type> IDENT "<-" <expr> ";"`

```java
private StaticDeclNode parseStaticDecl() {
    expect(KW_STATIC);
    String type = parseDeclType();
    String name = expect(IDENT).value;
    expect(ARROW);
    ASTNode init = parseExpr();
    expect(SEMICOLON);
    return new StaticDeclNode(type, name, init);
}
```

**`parseDeclType()`** — grammar: `<simple_type> | IDENT`

```java
private String parseDeclType() {
    TokenType t = peek().type;
    if (t == KW_INT || t == KW_FLOAT || t == KW_BOOL || t == KW_STRING)
        return advance().value;
    if (t == IDENT)
        return advance().value; // enum type name — validated later by the type checker
    Token tok = peek();
    throw new ParseError(String.format(
        "[Parser Error] Line %d: Expected a type name but found '%s'", tok.line, tok.value));
}
```

**`parseEnumDecl()`** — grammar: `"enum" IDENT "{" IDENT { "," IDENT } "}"`

```java
private EnumDeclNode parseEnumDecl() {
    expect(KW_ENUM);
    String name = expect(IDENT).value;
    expect(LBRACE);
    List<String> members = new ArrayList<>();
    members.add(expect(IDENT).value);
    while (check(COMMA)) { advance(); members.add(expect(IDENT).value); }
    expect(RBRACE);
    return new EnumDeclNode(name, members);
}
```

Follow the same direct-translation pattern for `parseSemaphoreDecl()`, `parseProcessDecl()`, `parseFuncDecl()`, `parseSystemDecl()`, `parseAddStmt()`, `parseRunStmt()`.

### B6 — Statements

**`parseBlock()`** — grammar: `"{" { <statement> } "}"`

```java
private BlockNode parseBlock() {
    expect(LBRACE);
    List<ASTNode> stmts = new ArrayList<>();
    while (!check(RBRACE) && !check(EOF)) stmts.add(parseStatement());
    expect(RBRACE);
    return new BlockNode(stmts);
}
```

**`parseIfStmt()`** — grammar: `"if" "(" <expr> ")" <block> { "elif" ... } [ "else" ... ]`

```java
private IfStmtNode parseIfStmt() {
    expect(KW_IF); expect(LPAREN);
    ASTNode cond = parseExpr();
    expect(RPAREN);
    BlockNode thenBlock = parseBlock();

    List<ElifClauseNode> elifClauses = new ArrayList<>();
    while (check(KW_ELIF)) {
        advance(); expect(LPAREN);
        ASTNode elifCond = parseExpr();
        expect(RPAREN);
        elifClauses.add(new ElifClauseNode(elifCond, parseBlock()));
    }

    BlockNode elseBlock = null;
    if (check(KW_ELSE)) { advance(); elseBlock = parseBlock(); }

    return new IfStmtNode(cond, thenBlock, elifClauses, elseBlock);
}
```

`parseWhileStmt()`, `parseReturnStmt()`, `parseAssignStmt()`, `parseCallStmt()`, `parseVarDeclStmt()` all follow the same direct-translation pattern.

### B7 — Expressions (the Precedence Cascade)

Translate the precedence cascade from README §5.24 Step 3 directly — one method per level. Every binary level follows the identical while-loop pattern.

```java
private ASTNode parseExpr()            { return parseOrExpr(); }

// <or_expr> ::= <and_expr> { "||" <and_expr> }
private ASTNode parseOrExpr() {
    ASTNode left = parseAndExpr();
    while (check(OR)) { String op = advance().value; left = new BinOpNode(op, left, parseAndExpr()); }
    return left;
}

// <and_expr> ::= <equality_expr> { "&&" <equality_expr> }
private ASTNode parseAndExpr() {
    ASTNode left = parseEqualityExpr();
    while (check(AND)) { String op = advance().value; left = new BinOpNode(op, left, parseEqualityExpr()); }
    return left;
}

// parseEqualityExpr, parseRelationalExpr, parseAdditiveExpr,
// parseMultiplicativeExpr — all follow the identical while-loop pattern.

// <unary_expr> ::= ( "!" | "-" ) <unary_expr> | <postfix_expr>
private ASTNode parseUnaryExpr() {
    if (check(NOT) || check(MINUS)) {
        String op = advance().value;
        return new UnaryOpNode(op, parseUnaryExpr()); // right-recursive — intentional
    }
    return parsePostfixExpr();
}

// <postfix_expr> ::= <primary> [ "." <process_attr> ]
private ASTNode parsePostfixExpr() {
    ASTNode node = parsePrimary();
    if (check(DOT)) { advance(); return new PostfixDotNode(node, parseProcessAttr()); }
    return node;
}

// <primary> ::= <literal> | IDENT [ "(" [ <arg_list> ] ")" ] | "(" <expr> ")"
private ASTNode parsePrimary() {
    Token t = peek();
    switch (t.type) {
        case INT_LIT:    advance(); return new IntLitNode(Integer.parseInt(t.value));
        case FLOAT_LIT:  advance(); return new FloatLitNode(Double.parseDouble(t.value));
        case BOOL_LIT:   advance(); return new BoolLitNode(t.value.equals("true"));
        case STRING_LIT: advance(); return new StringLitNode(t.value);
        case IDENT:
            advance();
            if (check(LPAREN)) {
                advance();
                List<ASTNode> args = new ArrayList<>();
                if (!check(RPAREN)) {
                    args.add(parseExpr());
                    while (check(COMMA)) { advance(); args.add(parseExpr()); }
                }
                expect(RPAREN);
                return new FuncCallNode(t.value, args);
            }
            return new IdentNode(t.value);
        case LPAREN:
            advance();
            ASTNode inner = parseExpr();
            expect(RPAREN);
            return inner;
        default:
            throw new ParseError(String.format(
                "[Parser Error] Line %d: Unexpected token '%s' in expression", t.line, t.value));
    }
}
```

**Why the loop is left-associative:** The while loop folds left: `a || b || c` → `BinOp(||, BinOp(||, a, b), c)`. The grammar's `{ }` repetition is neutral on associativity — Sebesta §3.3.2 p.127 is explicit about this. The left fold is the parser enforcing the design decision from README §5.13.

**Why unary recursion is right-associative:** `parseUnaryExpr()` recurses on itself after consuming the operator. `!!x` → `UnaryOp(!, UnaryOp(!, x))`. This is correct — unary operators always bind right-to-left.

### B8 — AST Dump Output

The handout D2 requires `--dump-ast`. Implement `dump(int indent)` on every node. Indented text is sufficient:

```
ProgramNode
  StaticDeclNode type=int name=maxTick
    IntLitNode 100
  FuncDeclNode name=isBlocked returns=bool
    ParamNode p:process
    BlockNode
      ReturnStmtNode
        BinOpNode ==
          PostfixDotNode .state
            IdentNode p
          IdentNode blocked
```

### B9 — Error Message Format

Every parse error must include the line number:

```
[Parser Error] Line 5: Expected ';' but found '}'
[Parser Error] Line 12: Unexpected token 'float' at top level
[Parser Error] Line 8: Expected a type name but found '42'
[Parser Error] Line 1: Empty program — at least one declaration is required
```

---

## Integration

Wire both components in `Main.java`:

```java
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: Main <file.osl> [--dump-tokens | --dump-ast]");
            System.exit(1);
        }
        String source = Files.readString(Path.of(args[0]));
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        if (args.length > 1 && args[1].equals("--dump-tokens")) {
            tokens.forEach(System.out::println);
            return;
        }

        Parser parser = new Parser(tokens);
        ProgramNode ast = parser.parseProgram();

        if (args.length > 1 && args[1].equals("--dump-ast")) {
            ast.dump(0);
        } else {
            System.out.println("OK — parsed successfully.");
        }
    }
}
```

**D2 README build and run commands** (required by the handout):

```
javac -d out src/*.java
java -cp out Main program.osl               # parse only
java -cp out Main program.osl --dump-ast    # parse and print AST
java -cp out Main program.osl --dump-tokens # print token stream only
```

---

## D1 Writing Guide

### Partner A writes — §4.2 Lexical Structure

Cover every token category with its pattern:

| Category | Pattern | Examples |
|---|---|---|
| `IDENT` | `[a-zA-Z_][a-zA-Z0-9_]*` | `x`, `mutex`, `P1` |
| `INT_LIT` | `[0-9]+` | `0`, `42`, `100` |
| `FLOAT_LIT` | `[0-9]+ '.' [0-9]+` | `3.14`, `0.5` |
| `BOOL_LIT` | `true` or `false` | `true`, `false` |
| `STRING_LIT` | `'"' { char or escape } '"'` | `"hello"`, `"line\n"` |

Then list: all reserved keywords (from README §5.24 Step 5), all operators with the maximal-munch disambiguation table, separators, and the comment rule (`//` to end of line, no block comments).

### Partner B writes — §4.3 Syntax

Paste the complete EBNF from README §5.24 Steps 1–5. Add three annotations:

- **LL(2) disambiguation:** where and why — the `peek2()` logic at the statement level (Sebesta §4.4.1).
- **Left-associativity:** grammar uses `{ }` repetition (neutral per Sebesta §3.3.2 p.127); parser loop enforces left folding (README §5.13).
- **No dangling-else:** every branch body is a mandatory `<block>` (Sebesta §3.3.1.4).

---

## D3 Test Programs

### Three valid programs

Each must exercise: at least one control structure, the enum or process structured type, at least one function definition and call, and at least one domain-specific construct (`run` or `add`). Must parse successfully — execution not required for Part 1.

### Five malformed programs

**Partner A (lexer errors):**
- Unclosed string: `print("hello;` → `[Lexer Error] Line N: Unterminated string literal`
- Invalid character: `int x <- 5@3;` → `[Lexer Error] Line N: Unexpected character '@'`
- Bad float: `float f <- 3.;` → document whatever your lexer produces

**Partner B (parser errors):**
- Missing semicolon: `static int x <- 5` → `[Parser Error] Line N: Expected ';' but found EOF`
- Function missing `->`: `func f() { return 1; }` → `[Parser Error] Line N: Expected '->' ...`
- `if` without braces: `if (x > 0) x <- 1;` → `[Parser Error] Line N: Expected '{' ...`

---

## Exam Reference Table

Maps every implementation decision to the Sebesta section each partner must know for the exam on 14 May.

| Decision | Partner | Sebesta |
|---|---|---|
| Lexeme vs token | A | §4.2 |
| State transition diagrams for lexer | A | §4.2 |
| Keywords recognized in lexer, not grammar | A | §4.2, §3.3.2 |
| `true`/`false` as `BOOL_LIT` tokens not grammar keywords | A | §3.3.2 (Issue 4 in README) |
| String — single-line restriction, escape sequences | A | §6.3, §1.3 |
| Maximal munch — `<-` vs `<=` vs `<` | A | §4.2 |
| Recursive descent — one method per non-terminal | B | §4.4 |
| LL(2) disambiguation at statements | B | §4.4.1 |
| Left-associativity via loop, not left recursion | B | §3.3.2 p.127, §5.13 |
| Right-associativity of unary via recursion | B | §3.3.2, §7.2 |
| Dangling-else absent — mandatory blocks | B | §3.3.1.4 |
| Mandatory initializer in var declarations | B | §5.4.2, §5.4.3.2 |
| Assignment as statement only, never expression | B | §7.7.5 |
| `elif` as single keyword token | B | §5.23, design decision |

---

*Last updated: 8 May 2026.*