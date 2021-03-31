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

    private String toFnArgRef(Names fnArgNames, String fnArgName) {
        int i = fnArgNames.indexOf(fnArgName);
        return String.format("[bp:%d]", i + 2);
    }

    private String toLvarRef(Names lvarNames, String lvarName) {
        int i = lvarNames.indexOf(lvarName);
        return String.format("[bp:-%d]", i + 1);
    }

    private void puts(String line, Object ... params) {
        System.out.print(String.format(line + "\n", params));
    }

    // --------------------------------

    private void genVar(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        puts("  sub_sp 1");

        if (stmtRest.size() == 2) {
            genSet(fnArgNames, lvarNames, stmtRest);
        }
    }

    private void genExpr_push(Names fnArgNames, Names lvarNames, NodeItem val) {
        String pushArg;
        switch (val.type) {
        case INT:
            pushArg = String.valueOf(val.getIntVal());
            break;
        case STR:
            if (fnArgNames.contains(val.getStrVal())) {
                String fnArgName = val.getStrVal();
                pushArg = toFnArgRef(fnArgNames, fnArgName);
            } else if (lvarNames.contains(val.getStrVal())) {
                String lvarName = val.getStrVal();
                pushArg = toLvarRef(lvarNames, lvarName);
            } else {
                throw unsupported(val);
            }
            break;
        case LIST:
            _genExpr_binary(fnArgNames, lvarNames, val);
            pushArg = "reg_a";
            break;
        default:
            throw invalidType(val);
        }

        puts("  push " + pushArg);
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

        puts("  set_reg_a 0");
        puts("  jump %s", labelEnd);

        puts("label %s", labelThen);
        puts("  set_reg_a 1");

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

        puts("  set_reg_a 1");
        puts("  jump %s", labelEnd);

        puts("label %s", labelThen);
        puts("  set_reg_a 0");

        puts("label %s", labelEnd);
    }

    private void _genExpr_binary(Names fnArgNames, Names lvarNames, NodeItem expr) {
        NodeItem operator = expr.getItems().first();
        NodeList args = expr.getItems().rest();

        NodeItem termL = args.get(0);
        NodeItem termR = args.get(1);

        genExpr_push(fnArgNames, lvarNames, termL);
        genExpr_push(fnArgNames, lvarNames, termR);

        if      (operator.strEq("+"  )) { genExpr_add() ; }
        else if (operator.strEq("*"  )) { genExpr_mult(); }
        else if (operator.strEq("eq" )) { genExpr_eq()  ; }
        else if (operator.strEq("neq")) { genExpr_neq() ; }
        else {
            throw unsupported(operator);
        }
    }

    private void genCall_pushFnArg(Names fnArgNames, Names lvarNames, NodeItem fnArg) {
        String pushArg;

        switch (fnArg.type) {
        case INT:
            pushArg = String.format("%d", fnArg.getIntVal());
            break;

        case STR:
            String fnArgStr = fnArg.getStrVal();
            if (fnArgNames.contains(fnArgStr)) {
                pushArg = toFnArgRef(fnArgNames, fnArgStr);
            } else if (lvarNames.contains(fnArgStr)) {
                pushArg = toLvarRef(lvarNames, fnArgStr);
            } else {
                throw unsupported(fnArg);
            }
            break;

        default:
            throw unsupported(fnArg);
        }

        puts("  push %s", pushArg);
    }

    private void genCall(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        String fnName = stmtRest.first().getStrVal();
        NodeList fnArgs = stmtRest.rest();

        for (NodeItem fnArg : fnArgs.reverse().getList()) {
            genCall_pushFnArg(fnArgNames, lvarNames, fnArg);
        }

        genVmComment("call  " + fnName);
        puts("  call %s", fnName);

        puts("  add_sp %d", fnArgs.size());
    }

    private void genCallSet(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        String lvarName = stmtRest.first().getStrVal();
        NodeList fnTemp = stmtRest.get(1).getItems();

        String fnName = fnTemp.first().getStrVal();
        NodeList fnArgs = fnTemp.rest();

        for (NodeItem fnArg : fnArgs.reverse().getList()) {
            genCall_pushFnArg(fnArgNames, lvarNames, fnArg);
        }

        genVmComment("call_set  " + fnName);
        puts("  call %s", fnName);
        puts("  add_sp %d", fnArgs.size());

        String ref = toLvarRef(lvarNames, lvarName);
        puts("  cp reg_a %s", ref);
    }

    private Optional<Integer> matchVramAddr(String str) {
        Regex re = new Regex();

        if (re.match("^vram\\[(\\d+)\\]$", str)) {
            return Optional.of(Integer.valueOf(re.group(1)));
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> matchVramRef(String str) {
        Regex re = new Regex();

        if (re.match("^vram\\[([a-z][a-z0-9_]*)\\]$", str)) {
            return Optional.of(re.group(1));
        } else {
            return Optional.empty();
        }
    }

    private void genSet(Names fnArgNames, Names lvarNames, NodeList rest) {
        NodeItem dest = rest.get(0);
        NodeItem expr = rest.get(1);

        String srcVal;
        switch (expr.type) {
        case INT:
            srcVal = String.valueOf(expr.getIntVal());
            break;

        case STR:
            String exprStr = expr.getStrVal();
            if (fnArgNames.contains(exprStr)) {
                srcVal = toFnArgRef(fnArgNames, exprStr);
            } else if (lvarNames.contains(exprStr)) {
                srcVal = toLvarRef(lvarNames, exprStr);
            } else if (matchVramAddr(exprStr).isPresent()) {
                int vramAddr = matchVramAddr(exprStr).get();
                puts("  get_vram %d reg_a", vramAddr);
                srcVal = "reg_a";
            } else if (matchVramRef(exprStr).isPresent()) {
                String vramRef = matchVramRef(exprStr).get();

                if (lvarNames.contains(vramRef)) {
                    String ref = toLvarRef(lvarNames, vramRef);
                    puts("  get_vram %s reg_a", ref);
                } else {
                    throw unsupported(expr);
                }
                srcVal = "reg_a";

            } else {
                throw unsupported(expr);
            }
            break;

        case LIST:
            _genExpr_binary(fnArgNames, lvarNames, expr);
            srcVal = "reg_a";
            break;

        default:
            throw invalidType(expr);
        }

        String destStr = dest.getStrVal();
        if (lvarNames.contains(destStr)) {
            String lvarRef = toLvarRef(lvarNames, destStr);
            puts("  cp %s %s", srcVal, lvarRef);
        } else if (matchVramAddr(destStr).isPresent()) {
            int vramAddr = matchVramAddr(destStr).get();
            puts("  set_vram %d %s", vramAddr, srcVal);
        } else if (matchVramRef(destStr).isPresent()) {

            String vramRef = matchVramRef(destStr).get();

            if (lvarNames.contains(vramRef)) {
                String ref = toLvarRef(lvarNames, vramRef);
                puts("  set_vram %s %s", ref, srcVal);
            } else {
                throw unsupported(vramRef);
            }

        } else {
            throw unsupported(destStr);
        }
    }

    private void genReturn(Names lvarNames, NodeList stmtRest) {
        NodeItem retval = stmtRest.first();

        switch (retval.type) {
        case INT:
            puts("  cp %d reg_a", retval.getIntVal());
            break;

        case STR:
            String retvalStr = retval.getStrVal();

            if (matchVramRef(retvalStr).isPresent()) {

                String vramRef = matchVramRef(retvalStr).get();
                
                if (lvarNames.contains(vramRef)) {
                    String ref = toLvarRef(lvarNames, vramRef);
                    puts("  get_vram %s reg_a", ref);
                } else {
                    throw unsupported(retval);
                }

            } else if (lvarNames.contains(retvalStr)) {
                String ref = toLvarRef(lvarNames, retvalStr);
                puts("  cp %s reg_a", ref);
            } else {
                throw unsupported(retval);
            }
            break;

        default:
            throw unsupported(retval);
        }
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
        _genExpr_binary(fnArgNames, lvarNames, condExpr);
        puts("  set_reg_b 1");
        puts("  compare");

        puts("  jump_eq %s", labelTrue);

        puts("  jump %s", labelEnd);

        puts("label %s", labelTrue);
        genStmts(fnArgNames, lvarNames, body);
        
        puts("  jump %s", labelBegin);

        puts("label %s", labelEnd);
        puts("");
    }

    private void genCase(Names fnArgNames, Names lvarNames, NodeList whenBlocks) {
        int labelId = CodeGenerator.nextLabelId();

        int whenIdx = -1;

        String labelEnd = String.format("end_case_%d", labelId);
        String labelWhenHead = String.format("when_%d", labelId);
        String labelEndWhenHead = String.format("end_when_%d", labelId);

        for (NodeItem _whenBlock : whenBlocks.getList()) {
            NodeList whenBlock = _whenBlock.getItems();
            whenIdx++;

            NodeItem cond = whenBlock.first();
            NodeList rest = whenBlock.rest();

            NodeItem condHead = cond.getItems().first();

            puts(
                    "  # 条件 %d_%d: %s",
                    labelId, whenIdx, cond.inspect()
                    );

            if (condHead.strEq("eq")) {
                _genExpr_binary(fnArgNames, lvarNames, cond);
                puts("  set_reg_b 1");

                puts("  compare");
                puts("  jump_eq %s_%d", labelWhenHead, whenIdx);
                puts("  jump %s_%d", labelEndWhenHead, whenIdx);

                puts("label %s_%d", labelWhenHead, whenIdx);

                genStmts(fnArgNames, lvarNames, rest);

                puts("label %s_%d", labelEndWhenHead, whenIdx);
            } else {
                throw unsupported(condHead);
            }
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
        puts("  push bp");
        puts("  cp sp bp");

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
        puts("  cp bp sp");
        puts("  pop bp");
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

    void codegen(NodeList nl) {
        puts("  call main");
        puts("  exit");

        // NodeItem head = nl.first();
        NodeList rest = nl.rest();
        genTopStmts(rest);
    }

}
