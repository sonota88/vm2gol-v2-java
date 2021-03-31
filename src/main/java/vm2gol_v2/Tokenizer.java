package vm2gol_v2;

import vm2gol_v2.util.Regex;
import vm2gol_v2.util.Utils;
import vm2gol_v2.type.Token;
import vm2gol_v2.type.Token.Kind;

import static vm2gol_v2.util.Utils.unexpected;
import static vm2gol_v2.util.Utils.putskv_e;

import java.util.ArrayList;
import java.util.List;

class Tokenizer {

    public static void run() {
        new Tokenizer().main(); 
    }

    private void main() {
        String src = Utils.readStdinAll();

        List<Token> tokens = tokenize(src);

        printTokens(tokens);
    }

    // --------------------------------

    private List<Token> tokenize(String src) {
        List<Token> tokens = new ArrayList<>();
        int pos = 0;

        Regex re = new Regex();

        while (pos < src.length()) {
            String rest = src.substring(pos);

            if (re.match("^([ \n]+)", rest)) {
                String s = re.group(1);
                pos += s.length();

            } else if (re.match("^(//.*)", rest)) {
                String s = re.group(1);
                pos += s.length();

            } else if (re.match("^\"(.*)\"", rest)) {
                String s = re.group(1);
                tokens.add(new Token(Kind.STR, s));
                pos += s.length() + 2;

            } else if (re.match("^(func|set|var|call_set|call|return|case|while|_cmt)[^a-z_]", rest)) {
                String s = re.group(1);
                tokens.add(new Token(Kind.KW, s));
                pos += s.length();

            } else if (re.match("^(-?[0-9]+)", rest)) {
                String s = re.group(1);
                tokens.add(new Token(Kind.INT, s));
                pos += s.length();

            } else if (re.match("^(==|!=|[(){}=;+*,])", rest)) {
                String s = re.group(1);
                tokens.add(new Token(Kind.SYM, s));
                pos += s.length();

            } else if (re.match("^([a-z_][a-z0-9_]*)", rest)) {
                String s = re.group(1);
                tokens.add(new Token(Kind.IDENT, s));
                pos += s.length();

            } else {
                String pre = src.substring(0, pos);
                String post = src.substring(pos);

                putskv_e("pre", Utils.escape(pre));
                putskv_e("post", Utils.escape(post));

                throw unexpected("Unexpected pattern");
            }
        }

        return tokens;
    }

    private void printTokens(List<Token> tokens) {
        for (Token t : tokens) {
            System.out.print(t.toLine() + Utils.LF);
        }
    }

}
