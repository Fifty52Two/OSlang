public class Token {

    public final TokenType type;
    public final String    value;  // raw lexeme as it appeared in source
    public final int       line;   // 1-based line number for error messages

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