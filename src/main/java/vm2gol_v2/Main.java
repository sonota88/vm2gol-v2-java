package vm2gol_v2;

public class Main {

    public static void main(String[] args) {
        try {
            new Main()._main(args);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void _main(String[] args) {
        String cmd = args[0];

        switch (cmd) {
        case "tokenize": Tokenizer.run()    ; break;
        case "parse"   : Parser.run()       ; break;
        case "codegen" : CodeGenerator.run(); break;
        case "test_json": JsonTester.run(); break;
        default:
            throw new IllegalArgumentException(cmd);
        }
    }

}
