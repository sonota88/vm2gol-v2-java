package vm2gol_v2;

import org.apache.commons.lang3.StringUtils;

import vm2gol_v2.util.Json;
import vm2gol_v2.util.Utils;
import vm2gol_v2.type.Names;
import vm2gol_v2.type.NodeItem;
import vm2gol_v2.type.NodeList;

import static vm2gol_v2.util.Utils.invalidType;
import static vm2gol_v2.util.Utils.unsupported;

public class CodeGenerator {

    public static void run() {
        new CodeGenerator().main();
    }

    private void main() {
        String src = Utils.readStdinAll();

        NodeList tree = Json.parse(src);

        codegen(tree);
    }

    // --------------------------------

    private static int labelId = 0;

    public static int nextLabelId() {
        CodeGenerator.labelId++;
        return CodeGenerator.labelId;
    }

    private int fnArgDisp(Names fnArgNames, String fnArgName) {
        int i = fnArgNames.indexOf(fnArgName);
        return i + 2;
    }

    private int lvarDisp(Names lvarNames, String lvarName) {
        int i = lvarNames.indexOf(lvarName);
        return -(i + 1);
    }

    private void puts(String line, Object ... params) {
        System.out.print(String.format(line + "\n", params));
    }

    // --------------------------------

    private void asmPrologue() {
        puts("  push bp");
        puts("  cp sp bp");
    }

    private void asmEpilogue() {
        puts("  cp bp sp");
        puts("  pop bp");
    }

    // --------------------------------

    private void genExpr_add() {
        puts("  pop reg_b");
        puts("  pop reg_a");

        puts("  add_ab");
    }

    private void genExpr_mult() {
        puts("  pop reg_b");
        puts("  pop reg_a");

        puts("  mult_ab");
    }

    private void genExpr_eq() {
        int labelId = CodeGenerator.nextLabelId();
        String labelThen = String.format("then_%d", labelId);
        String labelEnd = String.format("end_eq_%d", labelId);

        puts("  pop reg_b");
        puts("  pop reg_a");

        puts("  compare");
        puts("  jump_eq %s", labelThen);

        puts("  cp 0 reg_a");
        puts("  jump %s", labelEnd);

        puts("label %s", labelThen);
        puts("  cp 1 reg_a");

        puts("label %s", labelEnd);
    }

    private void genExpr_neq() {
        int labelId = CodeGenerator.nextLabelId();
        String labelThen = String.format("then_%d", labelId);
        String labelEnd = String.format("end_neq_%d", labelId);

        puts("  pop reg_b");
        puts("  pop reg_a");

        puts("  compare");
        puts("  jump_eq %s", labelThen);

        puts("  cp 1 reg_a");
        puts("  jump %s", labelEnd);

        puts("label %s", labelThen);
        puts("  cp 0 reg_a");

        puts("label %s", labelEnd);
    }

    private void _genExpr_binary(Names fnArgNames, Names lvarNames, NodeItem expr) {
        NodeItem operator = expr.getItems().first();
        NodeList args = expr.getItems().rest();

        NodeItem termL = args.get(0);
        NodeItem termR = args.get(1);

        genExpr(fnArgNames, lvarNames, termL);
        puts("  push reg_a");
        genExpr(fnArgNames, lvarNames, termR);
        puts("  push reg_a");

        if      (operator.strEq("+" )) { genExpr_add() ; }
        else if (operator.strEq("*" )) { genExpr_mult(); }
        else if (operator.strEq("==")) { genExpr_eq()  ; }
        else if (operator.strEq("!=")) { genExpr_neq() ; }
        else {
            throw unsupported(operator);
        }
    }

    private void genExpr(Names fnArgNames, Names lvarNames, NodeItem expr) {
        switch (expr.type) {
        case INT:
            puts("  cp %d reg_a", expr.getIntVal());
            break;
        case STR:
            if (lvarNames.contains(expr.getStrVal())) {
                String lvarName = expr.getStrVal();
                int disp = lvarDisp(lvarNames, lvarName);
                puts("  cp [bp:%d] reg_a", disp);
            } else if (fnArgNames.contains(expr.getStrVal())) {
                String fnArgName = expr.getStrVal();
                int disp = fnArgDisp(fnArgNames, fnArgName);
                puts("  cp [bp:%d] reg_a", disp);
            } else {
                throw unsupported(expr);
            }
            break;
        case LIST:
            _genExpr_binary(fnArgNames, lvarNames, expr);
            break;
        default:
            throw invalidType(expr);
        }
    }

    private void genFuncall(Names fnArgNames, Names lvarNames, NodeList funcall) {
        String fnName = funcall.first().getStrVal();
        NodeList fnArgs = funcall.rest();

        for (NodeItem fnArg : fnArgs.reverse().getList()) {
            genExpr(fnArgNames, lvarNames, fnArg);
            puts("  push reg_a");
        }

        genVmComment("call  " + fnName);
        puts("  call %s", fnName);

        puts("  add_sp %d", fnArgs.size());
    }

    private void genCall(Names fnArgNames, Names lvarNames, NodeList stmt) {
        NodeList funcall = stmt.get(1).getItems();
        genFuncall(fnArgNames, lvarNames, funcall);
    }

    private void genCallSet(Names fnArgNames, Names lvarNames, NodeList stmt) {
        String lvarName = stmt.get(1).getStrVal();
        NodeList funcall = stmt.get(2).getItems();

        genFuncall(fnArgNames, lvarNames, funcall);

        int disp = lvarDisp(lvarNames, lvarName);
        puts("  cp reg_a [bp:%d]", disp);
    }

    private void _genSet(Names fnArgNames, Names lvarNames, NodeItem dest, NodeItem expr) {
        genExpr(fnArgNames, lvarNames, expr);
        String srcVal = "reg_a";

        String destStr = dest.getStrVal();
        if (lvarNames.contains(destStr)) {
            int disp = lvarDisp(lvarNames, destStr);
            puts("  cp %s [bp:%d]", srcVal, disp);
        } else {
            throw unsupported(destStr);
        }
    }

    private void genSet(Names fnArgNames, Names lvarNames, NodeList stmt) {
        NodeItem dest = stmt.get(1);
        NodeItem expr = stmt.get(2);

        _genSet(fnArgNames, lvarNames, dest, expr);
    }

    private void genReturn(Names lvarNames, NodeList stmt) {
        NodeItem retval = stmt.get(1);
        genExpr(new Names(), lvarNames, retval);
        asmEpilogue();
        puts("  ret");
    }

    private void genWhile(Names fnArgNames, Names lvarNames, NodeList stmt) {
        NodeItem condExpr = stmt.get(1);
        NodeList stmts = stmt.get(2).getItems();

        int labelId = CodeGenerator.nextLabelId();

        String labelBegin = String.format("while_%d", labelId);
        String labelEnd = String.format("end_while_%d", labelId);

        puts("");

        puts("label %s", labelBegin);

        // 条件の評価
        genExpr(fnArgNames, lvarNames, condExpr);
        puts("  cp 0 reg_b");
        puts("  compare");

        puts("  jump_eq %s", labelEnd);

        genStmts(fnArgNames, lvarNames, stmts);

        puts("  jump %s", labelBegin);

        puts("label %s", labelEnd);
        puts("");
    }

    private void genCase(Names fnArgNames, Names lvarNames, NodeList stmt) {
        NodeList whenClauses = stmt.rest();
        int labelId = CodeGenerator.nextLabelId();

        int whenIdx = -1;

        String labelEnd = String.format("end_case_%d", labelId);
        String labelEndWhenHead = String.format("end_when_%d", labelId);

        for (NodeItem _whenClause : whenClauses.getList()) {
            NodeList whenClause = _whenClause.getItems();
            whenIdx++;

            NodeItem cond = whenClause.first();
            NodeList stmts = whenClause.rest();

            puts(
                    "  # 条件 %d_%d: %s",
                    labelId, whenIdx, cond.inspect()
                    );

            genExpr(fnArgNames, lvarNames, cond);
            puts("  cp 0 reg_b");
            puts("  compare");

            puts("  jump_eq %s_%d", labelEndWhenHead, whenIdx);

            genStmts(fnArgNames, lvarNames, stmts);

            puts("  jump %s", labelEnd);

            puts("label %s_%d", labelEndWhenHead, whenIdx);
        }

        puts("label %s", labelEnd);
    }

    private void genVmComment(String comment) {
        puts("  _cmt " + StringUtils.replace(comment, " ", "~"));
    }

    private void genVmDebug() {
        puts("  _debug");
    }

    private void genStmt(Names fnArgNames, Names lvarNames, NodeList stmt) {
        String stmtHead = stmt.first().getStrVal();
        NodeList stmtRest = stmt.rest();

        switch (stmtHead) {
        case "set"     : genSet(    fnArgNames, lvarNames, stmt); break;
        case "call"    : genCall(   fnArgNames, lvarNames, stmt); break;
        case "call_set": genCallSet(fnArgNames, lvarNames, stmt); break;
        case "return"  : genReturn(             lvarNames, stmt); break;
        case "while"   : genWhile(  fnArgNames, lvarNames, stmt); break;
        case "case"    : genCase(   fnArgNames, lvarNames, stmt); break;
        case "_cmt"    : genVmComment(stmt.get(1).getStrVal())  ; break;
        case "_debug"  : genVmDebug()                           ; break;
        default:
            throw unsupported(stmtHead);
        }
    }

    private void genStmts(Names fnArgNames, Names lvarNames, NodeList stmts) {
        for (NodeItem _stmt : stmts.getList()) {
            NodeList stmt = _stmt.getItems();
            genStmt(fnArgNames, lvarNames, stmt);
        }
    }

    private void genVar(Names fnArgNames, Names lvarNames, NodeList stmt) {
        puts("  add_sp -1");

        if (stmt.size() == 3) {
            NodeItem dest = stmt.get(1);
            NodeItem expr = stmt.get(2);
            _genSet(fnArgNames, lvarNames, dest, expr);
        }
    }

    private void genFuncDef(NodeList funcDef) {
        String fnName = funcDef.get(1).getStrVal();
        Names fnArgNames = Names.fromNodeList(funcDef.get(2).getItems());
        NodeList body = funcDef.get(3).getItems();

        puts("");
        puts("label %s", fnName);
        asmPrologue();

        puts("");
        puts("  # 関数の処理本体");

        Names lvarNames = new Names();

        for (NodeItem stmt : body.getList()) {
            NodeList _stmt = stmt.getItems();
            if (_stmt.first().strEq("var")) {
                lvarNames.add(_stmt.get(1).getStrVal());
                genVar(fnArgNames, lvarNames, _stmt);
            } else {
                genStmt(fnArgNames, lvarNames, _stmt);
            }
        }

        puts("");
        asmEpilogue();
        puts("  ret");
    }

    private void genTopStmts(NodeList ast) {
        NodeList topStmts = ast.rest();

        for (NodeItem stmt : topStmts.getList()) {
            NodeItem stmtHead = stmt.getItems().first();
            if (stmtHead.strEq("func")) {
                genFuncDef(stmt.getItems());
            } else {
                throw unsupported(stmtHead);
            }
        }
    }

    private void genBuiltinSetVram() {
        puts("");
        puts("label set_vram");
        asmPrologue();
        puts("  set_vram [bp:2] [bp:3]"); // vram_addr value
        asmEpilogue();
        puts("  ret");
    }

    private void genBuiltinGetVram() {
        puts("");
        puts("label set_vram");
        asmPrologue();
        puts("  set_vram [bp:2] reg_a"); // vram_addr dest
        asmEpilogue();
        puts("  ret");
    }

    void codegen(NodeList ast) {
        puts("  call main");
        puts("  exit");

        genTopStmts(ast);

        puts("#>builtins");
        genBuiltinSetVram();
        genBuiltinGetVram();
        puts("#<builtins");
    }

}
