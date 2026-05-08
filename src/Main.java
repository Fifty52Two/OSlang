import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            System.err.println("Usage: java -cp out Main <file.osl> [--dump-tokens]");
            System.exit(1);
        }

        String source = Files.readString(Path.of(args[0]));

        Lexer lexer = new Lexer(source);
        List<Token> tokens;

        try {
            tokens = lexer.tokenize();
        } catch (RuntimeException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (args.length > 1 && args[1].equals("--dump-tokens")) {
            tokens.forEach(System.out::println);
            return;
        }

        System.out.println("OK — lexed successfully. " + tokens.size() + " tokens.");
    }
}