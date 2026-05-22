import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            System.err.println("Usage: java -cp out Main <file.osl> [--dump-tokens | --dump-ast]");
            System.exit(1);
        }

        // Scan args: first non-flag is the source file, then look for flags
        String sourcePath = null;
        boolean dumpTokens = false;
        boolean dumpAst    = false;
        for (String arg : args) {
            if (arg.equals("--dump-tokens"))      dumpTokens = true;
            else if (arg.equals("--dump-ast"))    dumpAst    = true;
            else if (sourcePath == null)          sourcePath = arg;
            else {
                System.err.println("Unknown argument: " + arg);
                System.exit(1);
            }
        }
        if (sourcePath == null) {
            System.err.println("Usage: java -cp out Main <file.osl> [--dump-tokens | --dump-ast]");
            System.exit(1);
        }

        String source = Files.readString(Path.of(sourcePath));

        // ── Lexer phase ──────────────────────────────────────────────────────
        Lexer lexer = new Lexer(source);
        List<Token> tokens;
        try {
            tokens = lexer.tokenize();
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        // --dump-tokens: print token stream and stop
        if (dumpTokens) {
            tokens.forEach(System.out::println);
            return;
        }

        // ── Parser phase ─────────────────────────────────────────────────────
        Parser parser = new Parser(tokens);
        ProgramNode ast;
        try {
            ast = parser.parseProgram();
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        // --dump-ast: print AST and stop
        if (dumpAst) {
            ast.dump(0);
            return;
        }

        // ── Type-check phase ─────────────────────────────────────────────────
        TypeChecker typeChecker = new TypeChecker();
        try {
            typeChecker.check(ast);
        } catch (RuntimeException e) {
            // TypeError extends RuntimeException — print and exit, do NOT run interpreter
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        // ── Interpreter phase ────────────────────────────────────────────────
        Interpreter interpreter = new Interpreter();
        try {
            interpreter.interpret(ast);
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
    }
}