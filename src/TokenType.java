public enum TokenType {

    // Literals
    INT_LIT,       // e.g. 42
    FLOAT_LIT,     // e.g. 3.14  (never .5 or 5.)
    BOOL_LIT,      // true | false  — enters via keyword table, emits literal token
    STRING_LIT,    // e.g. "hello"

    // Identifier
    IDENT,

    // Type and storage keywords
    KW_INT, KW_FLOAT, KW_BOOL, KW_STRING,
    KW_SEMAPHORE, KW_PROCESS, KW_SYSTEM,
    KW_ENUM, KW_STATIC, KW_FUNC,

    // Control flow keywords
    KW_IF, KW_ELIF, KW_ELSE,   // elif is ONE token, not else + if
    KW_WHILE, KW_RETURN,

    // Top-level operation keywords — strictly reserved, cannot be used as identifiers
    KW_RUN, KW_ADD,

    // Built-in operation keywords — reserved, cannot be redefined by the user
    KW_WAIT, KW_POST, KW_PRINT,

    // Scheduler keywords — all uppercase
    KW_FCFS, KW_SJF, KW_SRTF, KW_RR,
    KW_PRIORITY,      // uppercase PRIORITY — scheduler name

    // Field and attribute keywords — all lowercase
    KW_PROCESSES, KW_SCHEDULER, KW_QUANT,
    KW_BURST,
    KW_PRIORITY_F,    // lowercase priority — process field/attribute, distinct from KW_PRIORITY
    KW_ARRIVAL, KW_UNTIL, KW_STATE,

    // Operators
    ARROW,            // <-  assignment
    ARROW_RETURN,     // ->  function return type

    EQ, NEQ,          // == !=
    LT, GT, LEQ, GEQ, // <  >  <=  >=

    AND, OR, NOT,     // && || !

    PLUS, MINUS, STAR, SLASH, PERCENT,  // + - * / %

    // Separators
    LPAREN, RPAREN,      // ( )
    LBRACE, RBRACE,      // { }
    LBRACKET, RBRACKET,  // [ ]
    COMMA,               // ,
    SEMICOLON,           // ;
    COLON,               // :
    DOT,                 // .

    // End-of-input sentinel — lets the parser peek() safely past the last token
    EOF
}