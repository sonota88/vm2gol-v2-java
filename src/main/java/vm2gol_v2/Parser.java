package vm2gol_v2;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import vm2gol_v2.util.Json;
import vm2gol_v2.util.Utils;
import vm2gol_v2.type.NodeItem;
import vm2gol_v2.type.NodeList;
import vm2gol_v2.type.Token;

import static vm2gol_v2.util.Utils.notYetImpl;
import static vm2gol_v2.util.Utils.invalidKind;
import static vm2gol_v2.util.Utils.unexpected;
import static vm2gol_v2.util.Utils.unsupported;

public class Parser {

    public static void run() {
        new Parser().main();
    }

    private void main() {
        String src = Utils.readStdinAll();
        this.tokens = toTokens(src);

        NodeItem tree;
        try {
            tree = parse();
        } catch (Exception e) {
            // dumpState(); // TODO
            throw e;
        }

        System.out.print(toJson(tree));
    }

    // --------------------------------

    private int pos = 0;
    private List<Token> tokens;

    private List<Token> toTokens(String src) {
        String [] lines = StringUtils.split(src, Utils.LF);

        return Arrays.stream(lines)
                .map(Token::fromLine)
                .collect(Collectors.toList());
    }

    private String toJson(NodeItem tree) {
        return Json.toJson(tree.getItems());
    }

    // --------------------------------

    private boolean isEnd() {
        return this.tokens.size() <= this.pos;
    }

    private Token peek(int n) {
        return this.tokens.get(this.pos + n);
    }

    private Token peek() {
        return peek(0);
    }

    private void assertValue(Token t, Token.Kind kind, String expected) {
        if (t.kind != kind) {
            throw invalidKind(t);
        }

        if (Utils.strEq(t.getStr(), expected)) {
            // OK
        } else {
            String msg = String.format(
                    "Assertion failed: exp(%s) act(%s)",
                    expected,
                    t
                    );
            throw new RuntimeException(msg);
        }
    }

    private void assertValue_sym(int pos, String exp) {
        Token t = this.tokens.get(pos);

        assertValue(t, Token.Kind.SYM, exp);
    }

    private void assertValue_kw(int pos, String exp) {
        Token t = this.tokens.get(pos);

        assertValue(t, Token.Kind.KW, exp);
    }

    private void consumeKw(String s) {
        assertValue_kw(this.pos, s);
        this.pos++;
    }

    private void consumeSym(String s) {
        assertValue_sym(this.pos, s);
        this.pos++;
    }

    // --------------------------------

    private NodeList nodelist() {
        return new NodeList();
    }

    private NodeItem parseArg() {
        Token t = peek();

        switch (t.kind) {
        case IDENT:
            pos++;
            return NodeItem.of(t.getStr());

        case INT:
            pos++;
            return NodeItem.of(t.getIntVal());

        default:
            throw invalidKind(t);
        }
    }

    private NodeItem parseArgs_first() {
        if (peek().is(Token.Kind.SYM, ")")) {
            return null;
        }

        return parseArg();
    }

    private NodeItem parseArgs_rest() {
        if (peek().is(Token.Kind.SYM, ")")) {
            return null;
        }

        consumeSym(",");

        return parseArg();
    }

    private NodeList parseArgs() {
        NodeList args = nodelist();

        NodeItem firstArg = parseArgs_first();
        if (firstArg == null) {
            return args;
        }
        args.add(firstArg);

        while (true) {
            NodeItem restArg = parseArgs_rest();
            if (restArg == null) {
                break;
            }
            args.add(restArg);
        }

        return args;
    }

    private NodeList parseFunc() {
        consumeKw("func");

        Token t = peek();
        pos++;
        String fnName = t.getStr();

        consumeSym("(");
        NodeList args = parseArgs();
        consumeSym(")");

        consumeSym("{");

        NodeList stmts = new NodeList();
        while (! peek().is(Token.Kind.SYM, "}")) {
            if (peek().is(Token.Kind.KW, "var")) {
                stmts.add(parseVar());
            } else {
                stmts.add(parseStmt());
            }
        }
        consumeSym("}");

        return nodelist()
                .add("func")
                .add(fnName)
                .add(args)
                .add(stmts)
                ;
    }

    private NodeList parseVar_declare() {
        Token t = peek();
        this.pos++;
        String varName = t.getStr();

        consumeSym(";");

        return nodelist()
                .add("var")
                .add(varName)
                ;
    }

    private NodeList parseVar_init() {
        Token t = peek();
        this.pos++;
        String varName = t.getStr();

        consumeSym("=");

        NodeItem expr = parseExpr();

        consumeSym(";");

        return nodelist()
                .add("var")
                .add(varName)
                .add(expr)
                ;
    }

    private NodeList parseVar() {
        consumeKw("var");

        Token t = peek(1);

        if (t.strEq(";")) {
            return parseVar_declare();
        } else if (t.strEq("=")) {
            return parseVar_init();
        } else {
            throw unexpected("Unexpected token");
        }
    }

    private NodeItem parseExprRight(NodeItem exprL) {
        Token t = peek();

        if (t.is(Token.Kind.SYM, ";") || t.is(Token.Kind.SYM, ")")) {
            return exprL;
        }

        NodeList expr;

        if (t.is(Token.Kind.SYM, "+")) {
            consumeSym("+");
            NodeItem exprR = parseExpr();
            expr = nodelist()
                    .add("+")
                    .add(exprL)
                    .add(exprR)
                    ;
        } else if (t.is(Token.Kind.SYM, "*")) {
            consumeSym("*");
            NodeItem exprR = parseExpr();
            expr = nodelist()
                    .add("*")
                    .add(exprL)
                    .add(exprR)
                    ;
        } else if (t.is(Token.Kind.SYM, "==")) {
            consumeSym("==");
            NodeItem exprR = parseExpr();
            expr = nodelist()
                    .add("eq")
                    .add(exprL)
                    .add(exprR)
                    ;
        } else if (t.is(Token.Kind.SYM, "!=")) {
            consumeSym("!=");
            NodeItem exprR = parseExpr();
            expr = nodelist()
                    .add("neq")
                    .add(exprL)
                    .add(exprR)
                    ;
        } else {
            throw unsupported("Unsupported operator");
        }

        return NodeItem.of(expr);
    }

    private NodeItem parseExpr() {
        Token tl = peek();

        NodeItem exprL;

        switch (tl.kind) {
        case SYM:
            consumeSym("(");
            exprL = parseExpr();
            consumeSym(")");
            break;

        case INT:
            pos++;
            exprL = NodeItem.of(tl.getIntVal());
            break;

        case IDENT:
            pos++;
            exprL = NodeItem.of(tl.getStr());
            break;

        default:
            throw invalidKind(tl);
        }

        return parseExprRight(exprL);
    }

    private NodeList parseSet() {
        consumeKw("set");

        Token t = peek();
        pos++;
        String varName = t.getStr();

        consumeSym("=");

        NodeItem expr = parseExpr();

        consumeSym(";");

        return nodelist()
                .add("set")
                .add(varName)
                .add(expr)
                ;
    }

    private NodeList parseFuncall() {
        Token t = peek();
        pos++;
        String fnName = t.getStr();

        consumeSym("(");
        NodeList args = parseArgs();
        consumeSym(")");

        return nodelist()
                .add(fnName)
                .addAll(args)
                ;
    }

    private NodeList parseCall() {
        consumeKw("call");

        NodeList funcall = parseFuncall();

        consumeSym(";");

        return nodelist()
                .add("call")
                .addAll(funcall)
                ;
    }

    private NodeList parseCallSet() {
        consumeKw("call_set");

        Token t = peek();
        pos++;
        String varName = t.getStr();
        
        consumeSym("=");

        NodeList funcall = parseFuncall();

        consumeSym(";");

        return nodelist()
                .add("call_set")
                .add(varName)
                .add(funcall)
                ;
    }

    private NodeList parseReturn() {
        consumeKw("return");

        if (peek().is(Token.Kind.SYM, ";")) {
            // 引数なしの return
            throw notYetImpl(peek());
        } else {
            NodeItem expr = parseExpr();
            consumeSym(";");

            return nodelist()
                    .add("return")
                    .add(expr)
                    ;
        }
    }

    private NodeList parseWhile() {
        consumeKw("while");

        consumeSym("(");
        NodeItem expr = parseExpr();
        consumeSym(")");

        consumeSym("{");
        NodeList stmts = parseStmts();
        consumeSym("}");

        return nodelist()
                .add("while")
                .add(expr)
                .add(stmts)
                ;
    }

    private NodeList parseWhenClause() {
        Token t = peek();
        if (t.is(Token.Kind.SYM, "}")) {
            return NodeList.empty();
        }

        consumeSym("(");
        NodeItem expr = parseExpr();
        consumeSym(")");

        consumeSym("{");
        NodeList stmts = parseStmts();
        consumeSym("}");

        return nodelist()
                .add(expr)
                .addAll(stmts)
                ;
    }

    private NodeList parseCase() {
        consumeKw("case");

        consumeSym("{");

        NodeList whenClauses = nodelist();

        while (true) {
            NodeList whenClause = parseWhenClause();
            if (whenClause.isEmpty()) {
                break;
            }

            whenClauses.add(whenClause);
        }

        consumeSym("}");

        return nodelist()
                .add("case")
                .addAll(whenClauses)
                ;
    }

    private NodeList parseVmComment() {
        consumeKw("_cmt");
        consumeSym("(");

        Token t = peek();
        pos++;
        String comment = t.getStr();

        consumeSym(")");
        consumeSym(";");

        return nodelist()
                .add("_cmt")
                .add(comment)
                ;
    }

    private NodeList parseStmt() {
        Token t = peek();

        switch (t.getStr()) {
        case "set"     : return parseSet();
        case "call"    : return parseCall();
        case "call_set": return parseCallSet();
        case "return"  : return parseReturn();
        case "while"   : return parseWhile();
        case "case"    : return parseCase();
        case "_cmt"    : return parseVmComment();
        default:
            throw unexpected("Unexpected token");
        }
    }

    private NodeList parseStmts() {
        NodeList stmts = new NodeList();

        while (! peek().is(Token.Kind.SYM, "}")) {
            stmts.add(parseStmt());
        }

        return stmts;
    }

    private NodeList parseTopStmt() {
        if (peek().is(Token.Kind.KW, "func")) {
            return parseFunc();
        } else {
            throw unexpected("Unexpected token");
        }
    }

    private NodeList parseTopStmts() {
        NodeList topStmts = new NodeList();
        while (! isEnd()) {
            topStmts.add(parseTopStmt());
        }
        return topStmts;
    }

    private NodeItem parse() {
        NodeList topStmts = parseTopStmts();

        return NodeItem.of(
                nodelist()
                .add("top_stmts")
                .addAll(topStmts)
                );
    }

}
