package vm2gol_v2;

import org.apache.commons.lang3.StringUtils;

import vm2gol_v2.util.Json;
import vm2gol_v2.util.Regex;
import vm2gol_v2.util.Utils;
import vm2gol_v2.type.Alines;
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

        Alines alines = codegen(tree);

        printAlines(alines);
    }

    // --------------------------------

    private static int labelId = 0;

    public static int nextLabelId() {
        CodeGenerator.labelId++;
        return CodeGenerator.labelId;
    }

    private String toFnArgRef(Names fnArgNames, String fnArgName) {
        int i = fnArgNames.indexOf(fnArgName);
        return String.format("[bp+%d]", i + 2);
    }

    private String toLvarRef(Names lvarNames, String lvarName) {
        int i = lvarNames.indexOf(lvarName);
        return String.format("[bp-%d]", i + 1);
    }

    private Alines genVar(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        Alines alines = new Alines();

        alines.add("  sub_sp 1");

        if (stmtRest.size() == 2) {
            alines.addAll(genSet(fnArgNames, lvarNames, stmtRest));
        }

        return alines;
    }

    private Alines genExp_push(Names fnArgNames, Names lvarNames, NodeItem val) {
        Alines alines = new Alines();
        
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
            alines.addAll(
                    genExp(fnArgNames, lvarNames, val)
                    );
            pushArg = "reg_a";
            break;
        default:
            throw invalidType(val);
        }

        alines.add("  push " + pushArg);
        
        return alines;
    }

    private Alines genExp_add() {
        Alines alines = new Alines();

        alines.add("  pop reg_b");
        alines.add("  pop reg_a");

        alines.add("  add_ab");

        return alines;
    }

    private Alines genExp_mult() {
        Alines alines = new Alines();

        alines.add("  pop reg_b");
        alines.add("  pop reg_a");

        alines.add("  mult_ab");

        return alines;
    }

    private Alines genExp_eq() {
        Alines alines = new Alines();

        int labelId = CodeGenerator.nextLabelId();
        String labelThen = String.format("then_%d", labelId);
        String endLabel = String.format("end_eq_%d", labelId);

        alines.add("  pop reg_b");
        alines.add("  pop reg_a");

        alines.add("  compare");
        alines.add("  jump_eq %s", labelThen);

        alines.add("  set_reg_a 0");
        alines.add("  jump %s", endLabel);

        alines.add("label %s", labelThen);
        alines.add("  set_reg_a 1");

        alines.add("label %s", endLabel);

        return alines;
    }

    private Alines genExp_neq() {
        Alines alines = new Alines();

        int labelId = CodeGenerator.nextLabelId();
        String labelThen = String.format("then_%d", labelId);
        String endLabel = String.format("end_neq_%d", labelId);
        
        alines.add("  pop reg_b");
        alines.add("  pop reg_a");

        alines.add("  compare");
        alines.add("  jump_eq %s", labelThen);

        alines.add("  set_reg_a 1");
        alines.add("  jump %s", endLabel);

        alines.add("label %s", labelThen);
        alines.add("  set_reg_a 0");

        alines.add("label %s", endLabel);

        return alines;
    }

    private Alines genExp(Names fnArgNames, Names lvarNames, NodeItem exp) {
        Alines alines = new Alines();

        NodeItem operator = exp.getItems().first();
        NodeList args = exp.getItems().rest();

        NodeItem termL = args.get(0);
        NodeItem termR = args.get(1);

        alines.addAll(genExp_push(fnArgNames, lvarNames, termL));
        alines.addAll(genExp_push(fnArgNames, lvarNames, termR));

        if      (operator.strEq("+"  )) { alines.addAll(genExp_add() ); }
        else if (operator.strEq("*"  )) { alines.addAll(genExp_mult()); }
        else if (operator.strEq("eq" )) { alines.addAll(genExp_eq()  ); }
        else if (operator.strEq("neq")) { alines.addAll(genExp_neq() ); }
        else {
            throw unsupported(operator);
        }

        return alines;
    }

    private Alines genCall_pushFnArg(Names fnArgNames, Names lvarNames, NodeItem fnArg) {
        Alines alines = new Alines();

        switch (fnArg.type) {
        case INT:
            alines.add("  push %d", fnArg.getIntVal());
            break;

        case STR:
            String fnArgStr = fnArg.getStrVal();
            if (fnArgNames.contains(fnArgStr)) {
                String ref = toFnArgRef(fnArgNames, fnArgStr);
                alines.add("  push %s", ref);
            } else if (lvarNames.contains(fnArgStr)) {
                String ref = toLvarRef(lvarNames, fnArgStr);
                alines.add("  push %s", ref);
            } else {
                throw unsupported(fnArg);
            }
            break;

        default:
            throw unsupported(fnArg);
        }

        return alines;
    }

    private Alines genCall(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        Alines alines = new Alines();

        String fnName = stmtRest.first().getStrVal();
        NodeList fnArgs = stmtRest.rest();

        for (NodeItem fnArg : fnArgs.reverse().getList()) {
            alines.addAll(
                    genCall_pushFnArg(fnArgNames, lvarNames, fnArg)
                    );
        }

        alines.addAll(genVmComment("call  " + fnName));
        alines.add("  call %s", fnName);

        alines.add("  add_sp %d", fnArgs.size());
        
        return alines;
    }

    private Alines genCallSet(Names fnArgNames, Names lvarNames, NodeList stmtRest) {
        Alines alines = new Alines();

        String lvarName = stmtRest.first().getStrVal();
        NodeList fnTemp = stmtRest.get(1).getItems();

        String fnName = fnTemp.first().getStrVal();
        NodeList fnArgs = fnTemp.rest();

        for (NodeItem fnArg : fnArgs.reverse().getList()) {
            alines.addAll(
                    genCall_pushFnArg(fnArgNames, lvarNames, fnArg)
                    );
        }

        alines.addAll(genVmComment("call_set  " + fnName));
        alines.add("  call %s", fnName);
        alines.add("  add_sp %d", fnArgs.size());

        String ref = toLvarRef(lvarNames, lvarName);
        alines.add("  cp reg_a %s", ref);

        return alines;
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

    private Alines genSet(Names fnArgNames, Names lvarNames, NodeList rest) {
        Alines alines = new Alines();

        NodeItem dest = rest.get(0);
        NodeItem exp = rest.get(1);

        String srcVal;
        switch (exp.type) {
        case INT:
            srcVal = String.valueOf(exp.getIntVal());
            break;

        case STR:
            String expStr = exp.getStrVal();
            if (fnArgNames.contains(expStr)) {
                srcVal = toFnArgRef(fnArgNames, expStr);
            } else if (lvarNames.contains(expStr)) {
                srcVal = toLvarRef(lvarNames, expStr);
            } else if (matchVramAddr(expStr).isPresent()) {
                int vramAddr = matchVramAddr(expStr).get();
                alines.add("  get_vram %d reg_a", vramAddr);
                srcVal = "reg_a";
            } else if (matchVramRef(expStr).isPresent()) {
                String vramRef = matchVramRef(expStr).get();

                if (lvarNames.contains(vramRef)) {
                    String ref = toLvarRef(lvarNames, vramRef);
                    alines.add("  get_vram %s reg_a", ref);
                } else {
                    throw unsupported(exp);
                }
                srcVal = "reg_a";

            } else {
                throw unsupported(exp);
            }
            break;

        case LIST:
            alines.addAll(genExp(fnArgNames, lvarNames, exp));
            srcVal = "reg_a";
            break;

        default:
            throw invalidType(exp);
        }

        String destStr = dest.getStrVal();
        if (lvarNames.contains(destStr)) {
            String lvarRef = toLvarRef(lvarNames, destStr);
            alines.add("  cp %s %s", srcVal, lvarRef);
        } else if (matchVramAddr(destStr).isPresent()) {
            int vramAddr = matchVramAddr(destStr).get();
            alines.add("  set_vram %d %s", vramAddr, srcVal);
        } else if (matchVramRef(destStr).isPresent()) {

            String vramRef = matchVramRef(destStr).get();

            if (lvarNames.contains(vramRef)) {
                String ref = toLvarRef(lvarNames, vramRef);
                alines.add("  set_vram %s %s", ref, srcVal);
            } else {
                throw unsupported(vramRef);
            }

        } else {
            throw unsupported(destStr);
        }

        return alines;
    }

    private Alines genReturn(Names lvarNames, NodeList stmtRest) {
        Alines alines = new Alines();

        NodeItem retval = stmtRest.first();

        switch (retval.type) {
        case INT:
            alines.add("  set_reg_a %d", retval.getIntVal());
            break;

        case STR:
            String retvalStr = retval.getStrVal();

            if (matchVramRef(retvalStr).isPresent()) {

                String vramRef = matchVramRef(retvalStr).get();
                
                if (lvarNames.contains(vramRef)) {
                    String ref = toLvarRef(lvarNames, vramRef);
                    alines.add("  get_vram %s reg_a", ref);
                } else {
                    throw unsupported(retval);
                }

            } else if (lvarNames.contains(retvalStr)) {
                String ref = toLvarRef(lvarNames, retvalStr);
                alines.add("  cp %s reg_a", ref);
            } else {
                throw unsupported(retval);
            }
            break;

        default:
            throw unsupported(retval);
        }
        
        return alines;
    }

    private Alines genVmComment(String comment) {
        Alines alines = new Alines();

        alines.add("  _cmt " + StringUtils.replace(comment, " ", "~"));
        
        return alines;
    }

    private Alines genWhile(Names fnArgNames, Names lvarNames, NodeList rest) {
        Alines alines = new Alines();

        NodeItem condExp = rest.first();
        NodeList body = rest.rest().first().getItems();

        int labelId = CodeGenerator.nextLabelId();

        String labelBegin = String.format("while_%d", labelId);
        String labelEnd = String.format("end_while_%d", labelId);
        String labelTrue = String.format("true_%d", labelId);

        alines.add("");

        alines.add("label %s", labelBegin);

        // 条件の評価
        alines.addAll(
                genExp(fnArgNames, lvarNames, condExp)
                );
        alines.add("  set_reg_b 1");
        alines.add("  compare");

        alines.add("  jump_eq %s", labelTrue);

        alines.add("  jump %s", labelEnd);

        alines.add("label %s", labelTrue);
        alines.addAll(
                genStmts(fnArgNames, lvarNames, body)
                );
        
        alines.add("  jump %s", labelBegin);

        alines.add("label %s", labelEnd);
        alines.add("");

        return alines;
    }

    private Alines genCase(Names fnArgNames, Names lvarNames, NodeList whenBlocks) {
        Alines alines = new Alines();

        int labelId = CodeGenerator.nextLabelId();

        int whenIdx = -1;
        List<Alines> thenBodies = new ArrayList<>(); 

        String labelEnd = String.format("end_case_%d", labelId);
        String labelWhenHead = String.format("when_%d", labelId);

        for (NodeItem _whenBlock : whenBlocks.getList()) {
            NodeList whenBlock = _whenBlock.getItems();
            whenIdx++;

            NodeItem cond = whenBlock.first();
            NodeList rest = whenBlock.rest();

            NodeItem condHead = cond.getItems().first();

            alines.add(
                    "  # 条件 %d_%d: %s",
                    labelId, whenIdx, cond.inspect()
                    );

            if (condHead.strEq("eq")) {
                alines.addAll(
                        genExp(fnArgNames, lvarNames, cond)
                        );
                alines.add("  set_reg_b 1");

                alines.add("  compare");
                alines.add("  jump_eq %s_%d", labelWhenHead, whenIdx);

                {
                    Alines thenAlines = new Alines();
                    thenAlines.add("label %s_%d", labelWhenHead, whenIdx);
                    thenAlines.addAll(
                            genStmts(fnArgNames, lvarNames, rest)
                            );
                    thenAlines.add("  jump %s", labelEnd);
    
                    thenBodies.add(thenAlines);
                }
            } else {
                throw unsupported(condHead);
            }
        }

        alines.add("  jump %s", labelEnd);

        for (Alines thenAlines : thenBodies) {
            alines.addAll(thenAlines);
        }

        alines.add("label %s", labelEnd);

        return alines;
    }

    private Alines genStmt(Names fnArgNames, Names lvarNames, NodeList stmt) {
        String stmtHead = stmt.first().getStrVal();
        NodeList stmtRest = stmt.rest();

        switch (stmtHead) {
        case "set"     : return genSet(    fnArgNames, lvarNames, stmtRest);
        case "call"    : return genCall(   fnArgNames, lvarNames, stmtRest);
        case "call_set": return genCallSet(fnArgNames, lvarNames, stmtRest);
        case "return"  : return genReturn(             lvarNames, stmtRest);
        case "while"   : return genWhile(  fnArgNames, lvarNames, stmtRest);
        case "case"    : return genCase(   fnArgNames, lvarNames, stmtRest);
        case "_cmt"    : return genVmComment(stmtRest.get(0).getStrVal());
        default:
            throw unsupported(stmtHead);
        }
    }

    private Alines genStmts(Names fnArgNames, Names lvarNames, NodeList stmts) {
        Alines alines = new Alines();

        for (NodeItem _stmt : stmts.getList()) {
            NodeList stmt = _stmt.getItems();
            alines.addAll(genStmt(fnArgNames, lvarNames, stmt));
        }

        return alines;
    }

    private Alines genFuncDef(NodeList rest) {
        Alines alines = new Alines();

        String fnName = rest.get(0).getStrVal();
        Names fnArgNames = Names.fromNodeList(rest.get(1).getItems());
        NodeList body = rest.get(2).getItems();

        alines.add("");
        alines.add("label %s", fnName);
        alines.add("  push bp");
        alines.add("  cp sp bp");

        alines.add("");
        alines.add("  # 関数の処理本体");

        Names lvarNames = new Names();

        for (NodeItem stmt : body.getList()) {
            NodeList _stmt = stmt.getItems();
            if (_stmt.first().strEq("var")) {
                NodeList stmtRest = _stmt.rest(); 
                lvarNames.add(stmtRest.first().getStrVal());
                alines.addAll(
                        genVar(fnArgNames, lvarNames, stmtRest)
                        );
            } else {
                alines.addAll(
                        genStmt(fnArgNames, lvarNames, _stmt)
                        );
            }
        }

        alines.add("");
        alines.add("  cp bp sp");
        alines.add("  pop bp");
        alines.add("  ret");

        return alines;
    }

    private Alines genTopStmts(NodeList rest) {
        Alines alines = new Alines();

        for (NodeItem stmt : rest.getList()) {
            NodeItem stmtHead = stmt.getItems().first();
            NodeList stmtRest = stmt.getItems().rest();

            if (stmtHead.strEq("func")) {
                alines.addAll(
                        genFuncDef(stmtRest)
                        );
            } else if (stmtHead.strEq("_cmt")) {
                alines.addAll(
                        genVmComment(stmtRest.first().getStrVal())
                        );
            } else {
                throw unsupported(stmtHead);
            }
        }

        return alines;
    }

    Alines codegen(NodeList nl) {
        Alines alines = new Alines();

        alines.add("  call main");
        alines.add("  exit");

        // NodeItem head = nl.first();
        NodeList rest = nl.rest();
        alines.addAll(
                genTopStmts(rest)
                );

        return alines;
    }

    private void printAlines(Alines alines) {
        for (String line : alines.getLines()) {
            System.out.print(line + Utils.LF);
        }
    }

}
