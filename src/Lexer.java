import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {

    // -------------------------------------------------------------------------
    // Keyword table
    // Read the full word first, then look it up here.
    // Hit  -> emit the keyword token
    // Miss -> emit IDENT
    // -------------------------------------------------------------------------
    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        // Type and storage
        KEYWORDS.put("int",       TokenType.KW_INT);
        KEYWORDS.put("float",     TokenType.KW_FLOAT);
        KEYWORDS.put("bool",      TokenType.KW_BOOL);
        KEYWORDS.put("string",    TokenType.KW_STRING);
        KEYWORDS.put("semaphore", TokenType.KW_SEMAPHORE);
        KEYWORDS.put("process",   TokenType.KW_PROCESS);
        KEYWORDS.put("system",    TokenType.KW_SYSTEM);
        KEYWORDS.put("enum",      TokenType.KW_ENUM);
        KEYWORDS.put("static",    TokenType.KW_STATIC);
        KEYWORDS.put("func",      TokenType.KW_FUNC);

        // Control flow
        KEYWORDS.put("if",        TokenType.KW_IF);
        KEYWORDS.put("elif",      TokenType.KW_ELIF);
        KEYWORDS.put("else",      TokenType.KW_ELSE);
        KEYWORDS.put("while",     TokenType.KW_WHILE);
        KEYWORDS.put("return",    TokenType.KW_RETURN);

        // Top-level operations
        KEYWORDS.put("run",       TokenType.KW_RUN);
        KEYWORDS.put("add",       TokenType.KW_ADD);

        // Built-in operations
        KEYWORDS.put("wait",      TokenType.KW_WAIT);
        KEYWORDS.put("post",      TokenType.KW_POST);
        KEYWORDS.put("print",     TokenType.KW_PRINT);

        // Schedulers — uppercase
        KEYWORDS.put("FCFS",      TokenType.KW_FCFS);
        KEYWORDS.put("SJF",       TokenType.KW_SJF);
        KEYWORDS.put("SRTF",      TokenType.KW_SRTF);
        KEYWORDS.put("RR",        TokenType.KW_RR);
        KEYWORDS.put("PRIORITY",  TokenType.KW_PRIORITY);

        // Field and attribute keywords
        KEYWORDS.put("processes", TokenType.KW_PROCESSES);
        KEYWORDS.put("scheduler", TokenType.KW_SCHEDULER);
        KEYWORDS.put("quant",     TokenType.KW_QUANT);
        KEYWORDS.put("burst",     TokenType.KW_BURST);
        KEYWORDS.put("priority",  TokenType.KW_PRIORITY_F);
        KEYWORDS.put("arrival",   TokenType.KW_ARRIVAL);
        KEYWORDS.put("until",     TokenType.KW_UNTIL);
        KEYWORDS.put("state",     TokenType.KW_STATE);

        // Boolean literals — emit BOOL_LIT, not a grammar keyword
        KEYWORDS.put("true",      TokenType.BOOL_LIT);
        KEYWORDS.put("false",     TokenType.BOOL_LIT);
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private final String source;  // entire source program
    private int pos  = 0;         // index of the next character to read
    private int line = 1;         // current line number, 1-based

    public Lexer(String source) {
        this.source = source;
    }

    // -------------------------------------------------------------------------
    // Cursor primitives
    // -------------------------------------------------------------------------

    // Returns true if we have consumed all characters
    private boolean atEnd() {
        return pos >= source.length();
    }

    // Returns the current character without consuming it
    // Returns '\0' if at end
    private char peek() {
        return atEnd() ? '\0' : source.charAt(pos);
    }

    // Returns the character after the current one without consuming anything
    // Returns '\0' if at end
    // Used for maximal munch: seeing '<' then checking if next is '-' or '='
    private char peekNext() {
        return (pos + 1 >= source.length()) ? '\0' : source.charAt(pos + 1);
    }

    // Consumes the current character and returns it
    // Increments line counter when it sees '\n'
    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') line++;
        return c;
    }

    // Consumes the current character only if it matches the expected character
    // Returns true if matched and consumed, false otherwise
    // Used for maximal munch: after seeing '<' call match('-') to get ARROW
    private boolean match(char expected) {
        if (atEnd() || source.charAt(pos) != expected) return false;
        advance();
        return true;
    }

    // -------------------------------------------------------------------------
    // Error
    // -------------------------------------------------------------------------

    // Halts on the first bad character — no error recovery in Part 1
    private RuntimeException error(String message) {
        return new RuntimeException("[Lexer Error] Line " + line + ": " + message);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    // Calls nextToken() in a loop until EOF and returns the full token list
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token t = nextToken();
            tokens.add(t);
            if (t.type == TokenType.EOF) break;
        }
        return tokens;
    }

    // Produces exactly one token per call
    public Token nextToken() {
        skipWhitespaceAndComments();

        // Capture the line where this token starts
        int startLine = line;

        if (atEnd()) {
            return new Token(TokenType.EOF, "", startLine);
        }

        char c = peek();

        // Letter -> identifier or keyword
        // README §5.5: identifiers must start with a latin letter, not '_'
        if (isLetter(c)) return readIdentifierOrKeyword(startLine);

        // Digit -> integer or float literal
        if (isDigit(c)) return readNumber(startLine);

        // Double quote -> string literal
        if (c == '"') return readString(startLine);

        // Everything else -> operator or punctuation
        return readOperatorOrPunctuation(startLine);
    }

    // -------------------------------------------------------------------------
    // Character class helpers
    // Explicitly ASCII only — README §5.5 specifies latin letters and digits
    // Java's Character.isLetter() accepts non-ASCII letters so we cannot use it
    // -------------------------------------------------------------------------

    private static boolean isLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // Letters, digits, and underscore — valid interior identifier characters
    private static boolean isIdentPart(char c) {
        return isLetter(c) || isDigit(c) || c == '_';
    }

    // -------------------------------------------------------------------------
    // Whitespace and comment skipper
    // -------------------------------------------------------------------------

    // Skips spaces, tabs, newlines, and // line comments
    // After this returns, peek() is the first character of a real token or we are at end
    private void skipWhitespaceAndComments() {
        while (!atEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                advance();
            } else if (c == '/' && peekNext() == '/') {
                // Line comment — skip everything until end of line
                while (!atEnd() && peek() != '\n') advance();
            } else {
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Identifier and keyword reader
    // -------------------------------------------------------------------------

    // On entry: peek() is a letter (guaranteed by nextToken dispatch)
    // Reads the maximal run of [a-zA-Z0-9_], then:
    //   - checks trailing underscore rule (README §5.5)
    //   - looks up in keyword table
    //   - emits keyword token on hit, IDENT on miss
    private Token readIdentifierOrKeyword(int startLine) {
        int start = pos;

        // Consume the maximal run of valid identifier characters
        while (!atEnd() && isIdentPart(peek())) advance();

        String lexeme = source.substring(start, pos);

        // README §5.5: identifier must not end with underscore
        if (lexeme.charAt(lexeme.length() - 1) == '_') {
            throw error("Identifier '" + lexeme + "' must not end with an underscore");
        }

        // Keyword table lookup — hit emits keyword token, miss emits IDENT
        TokenType type = KEYWORDS.getOrDefault(lexeme, TokenType.IDENT);
        return new Token(type, lexeme, startLine);
    }

    // -------------------------------------------------------------------------
    // Number reader
    // -------------------------------------------------------------------------

    // On entry: peek() is a digit (guaranteed by nextToken dispatch)
    // INT_LIT   pattern: [0-9]+
    // FLOAT_LIT pattern: [0-9]+ '.' [0-9]+
    // '5.' is NOT a float — the dot is only consumed if followed by another digit
    private Token readNumber(int startLine) {
        int start = pos;

        // Consume the integer part
        while (!atEnd() && isDigit(peek())) advance();

        // Check for float: dot must be followed by at least one digit
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // consume the dot
            while (!atEnd() && isDigit(peek())) advance(); // consume fractional part
            return new Token(TokenType.FLOAT_LIT, source.substring(start, pos), startLine);
        }

        return new Token(TokenType.INT_LIT, source.substring(start, pos), startLine);
    }

    // -------------------------------------------------------------------------
    // String reader
    // -------------------------------------------------------------------------

    // On entry: peek() is '"' (guaranteed by nextToken dispatch)
    // Reads until closing '"', handling escape sequences
    // A literal newline before the closing quote is a lexer error
    // The token value holds the decoded string — \n becomes an actual newline
    private Token readString(int startLine) {
        advance(); // consume opening '"'
        StringBuilder sb = new StringBuilder();

        while (!atEnd()) {
            char c = peek();

            if (c == '"') {
                advance(); // consume closing '"'
                return new Token(TokenType.STRING_LIT, sb.toString(), startLine);
            }

            if (c == '\n') {
                throw error("Unterminated string literal");
            }

            if (c == '\\') {
                advance(); // consume backslash
                if (atEnd()) throw error("Unterminated string literal");
                char esc = peek();
                switch (esc) {
                    case '"':  sb.append('"');  advance(); break;
                    case '\\': sb.append('\\'); advance(); break;
                    case 'n':  sb.append('\n'); advance(); break;
                    case 't':  sb.append('\t'); advance(); break;
                    default:
                        throw error("Invalid escape sequence '\\" + esc + "' in string literal");
                }
                continue;
            }

            sb.append(c);
            advance();
        }

        // Reached end of file without closing quote
        throw error("Unterminated string literal");
    }

    // -------------------------------------------------------------------------
    // Operator and punctuation reader
    // -------------------------------------------------------------------------

    // Handles all operators and punctuation using maximal munch
    // Single character tokens need no peeking
    // Multi character tokens peek at the next character before deciding
    private Token readOperatorOrPunctuation(int startLine) {
        char c = advance();
        switch (c) {

            // Single character punctuation
            case '(': return new Token(TokenType.LPAREN,    "(", startLine);
            case ')': return new Token(TokenType.RPAREN,    ")", startLine);
            case '{': return new Token(TokenType.LBRACE,    "{", startLine);
            case '}': return new Token(TokenType.RBRACE,    "}", startLine);
            case '[': return new Token(TokenType.LBRACKET,  "[", startLine);
            case ']': return new Token(TokenType.RBRACKET,  "]", startLine);
            case ',': return new Token(TokenType.COMMA,     ",", startLine);
            case ';': return new Token(TokenType.SEMICOLON, ";", startLine);
            case ':': return new Token(TokenType.COLON,     ":", startLine);
            case '.': return new Token(TokenType.DOT,       ".", startLine);

            // Single character arithmetic operators
            case '+': return new Token(TokenType.PLUS,    "+", startLine);
            case '*': return new Token(TokenType.STAR,    "*", startLine);
            case '%': return new Token(TokenType.PERCENT, "%", startLine);
            case '/': return new Token(TokenType.SLASH,   "/", startLine);

            // Maximal munch operators
            case '<':
                if (match('-')) return new Token(TokenType.ARROW,   "<-", startLine);
                if (match('=')) return new Token(TokenType.LEQ,     "<=", startLine);
                return new Token(TokenType.LT, "<", startLine);

            case '-':
                if (match('>')) return new Token(TokenType.ARROW_RETURN, "->", startLine);
                return new Token(TokenType.MINUS, "-", startLine);

            case '>':
                if (match('=')) return new Token(TokenType.GEQ, ">=", startLine);
                return new Token(TokenType.GT, ">", startLine);

            case '=':
                if (match('=')) return new Token(TokenType.EQ, "==", startLine);
                throw error("Unexpected character '='. Did you mean '==' or '<-'?");

            case '!':
                if (match('=')) return new Token(TokenType.NEQ, "!=", startLine);
                return new Token(TokenType.NOT, "!", startLine);

            case '&':
                if (match('&')) return new Token(TokenType.AND, "&&", startLine);
                throw error("Unexpected character '&'. Did you mean '&&'?");

            case '|':
                if (match('|')) return new Token(TokenType.OR, "||", startLine);
                throw error("Unexpected character '|'. Did you mean '||'?");

            default:
                throw error("Unexpected character '" + c + "'");
        }
    }

}