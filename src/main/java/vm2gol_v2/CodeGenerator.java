package vm2gol_v2;

import org.apache.commons.lang3.StringUtils;

import vm2gol_v2.util.Json;
import vm2gol_v2.util.Regex;
import vm2gol_v2.util.Utils;
import vm2gol_v2.type.Names;
import vm2gol_v2.type.NodeItem;
import vm2gol_v2.type.NodeList;

import static vm2gol_v2.util.Utils.invalidType;
import static vm2gol_v2.util.Utils.unsupported;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    private void genVar(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        puts("  sub_sp 1");

        if (stmtRest.size() == 2) {
            genSet(fnArgNames, lvarNames, stmtRest);
        }
    }

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

        if      (operator.strEq("+"  )) { genExpr_add() ; }
        else if (operator.strEq("*"  )) { genExpr_mult(); }
        else if (operator.strEq("eq" )) { genExpr_eq()  ; }
        else if (operator.strEq("neq")) { genExpr_neq() ; }
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

    private void genCall(Names fnArgNames, Names lvarNames, NodeList funcall) {
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

    private void genCallSet(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        String lvarName = stmtRest.first().getStrVal();
        NodeList funcall = stmtRest.get(1).getItems();

        genCall(fnArgNames, lvarNames, funcall);

        int disp = lvarDisp(lvarNames, lvarName);
        puts("  cp reg_a [bp:%d]", disp);
    }

    private void genSet(Names fnArgNames, Names lvarNames, NodeList rest) {
        NodeItem dest = rest.get(0);
        NodeItem expr = rest.get(1);

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

    private void genReturn(Names lvarNames, NodeList stmtRest) {
        NodeItem retval = stmtRest.first();
        genExpr(new Names(), lvarNames, retval);
    }

    private void genVmComment(String comment) {
        puts("  _cmt " + StringUtils.replace(comment, " ", "~"));
    }

    private void genWhile(Names fnArgNames, Names lvarNames, NodeList rest) {
        NodeItem condExpr = rest.first();
        NodeList body = rest.rest().first().getItems();

        int labelId = CodeGenerator.nextLabelId();

        String labelBegin = String.format("while_%d", labelId);
        String labelEnd = String.format("end_while_%d", labelId);
        String labelTrue = String.format("true_%d", labelId);

        puts("");

        puts("label %s", labelBegin);

        // 条件の評価
        genExpr(fnArgNames, lvarNames, condExpr);
        puts("  cp 1 reg_b");
        puts("  compare");

        puts("  jump_eq %s", labelTrue);

        puts("  jump %s", labelEnd);

        puts("label %s", labelTrue);
        genStmts(fnArgNames, lvarNames, body);
        
        puts("  jump %s", labelBegin);

        puts("label %s", labelEnd);
        puts("");
    }

    private void genCase(Names fnArgNames, Names lvarNames, NodeList whenClauses) {
        int labelId = CodeGenerator.nextLabelId();

        int whenIdx = -1;

        String labelEnd = String.format("end_case_%d", labelId);
        String labelWhenHead = String.format("when_%d", labelId);
        String labelEndWhenHead = String.format("end_when_%d", labelId);

        for (NodeItem _whenClause : whenClauses.getList()) {
            NodeList whenClause = _whenClause.getItems();
            whenIdx++;

            NodeItem cond = whenClause.first();
            NodeList rest = whenClause.rest();

            puts(
                    "  # 条件 %d_%d: %s",
                    labelId, whenIdx, cond.inspect()
                    );

            genExpr(fnArgNames, lvarNames, cond);
            puts("  cp 1 reg_b");

            puts("  compare");
            puts("  jump_eq %s_%d", labelWhenHead, whenIdx);
            puts("  jump %s_%d", labelEndWhenHead, whenIdx);

            puts("label %s_%d", labelWhenHead, whenIdx);

            genStmts(fnArgNames, lvarNames, rest);

            puts("label %s_%d", labelEndWhenHead, whenIdx);
        }

        puts("label %s", labelEnd);
    }

    private void genStmt(Names fnArgNames, Names lvarNames, NodeList stmt) {
        String stmtHead = stmt.first().getStrVal();
        NodeList stmtRest = stmt.rest();

        switch (stmtHead) {
        case "set"     : genSet(    fnArgNames, lvarNames, stmtRest); break;
        case "call"    : genCall(   fnArgNames, lvarNames, stmtRest); break;
        case "call_set": genCallSet(fnArgNames, lvarNames, stmtRest); break;
        case "return"  : genReturn(             lvarNames, stmtRest); break;
        case "while"   : genWhile(  fnArgNames, lvarNames, stmtRest); break;
        case "case"    : genCase(   fnArgNames, lvarNames, stmtRest); break;
        case "_cmt"    : genVmComment(stmtRest.get(0).getStrVal());   break;
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

    private void genFuncDef(NodeList rest) {
        String fnName = rest.get(0).getStrVal();
        Names fnArgNames = Names.fromNodeList(rest.get(1).getItems());
        NodeList body = rest.get(2).getItems();

        puts("");
        puts("label %s", fnName);
        asmPrologue();

        puts("");
        puts("  # 関数の処理本体");

        Names lvarNames = new Names();

        for (NodeItem stmt : body.getList()) {
            NodeList _stmt = stmt.getItems();
            if (_stmt.first().strEq("var")) {
                NodeList stmtRest = _stmt.rest(); 
                lvarNames.add(stmtRest.first().getStrVal());
                genVar(fnArgNames, lvarNames, stmtRest);
            } else {
                genStmt(fnArgNames, lvarNames, _stmt);
            }
        }

        puts("");
        asmEpilogue();
        puts("  ret");
    }

    private void genTopStmts(NodeList rest) {
        for (NodeItem stmt : rest.getList()) {
            NodeItem stmtHead = stmt.getItems().first();
            NodeList stmtRest = stmt.getItems().rest();

            if (stmtHead.strEq("func")) {
                genFuncDef(stmtRest);
            } else if (stmtHead.strEq("_cmt")) {
                genVmComment(stmtRest.first().getStrVal());
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

    void codegen(NodeList nl) {
        puts("  call main");
        puts("  exit");

        // NodeItem head = nl.first();
        NodeList rest = nl.rest();
        genTopStmts(rest);

        puts("#>builtins");
        genBuiltinSetVram();
        genBuiltinGetVram();
        puts("#<builtins");
    }

}
