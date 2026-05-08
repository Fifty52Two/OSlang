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

        String source = Files.readString(Path.of(args[0]));

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
        if (args.length > 1 && args[1].equals("--dump-tokens")) {
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
        if (args.length > 1 && args[1].equals("--dump-ast")) {
            ast.dump(0);
            return;
        }

        // Default: report success
        System.out.println("OK — parsed successfully.");
    }
}