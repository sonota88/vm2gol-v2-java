package vm2gol_v2.util;

import vm2gol_v2.type.NodeItem;
import vm2gol_v2.type.NodeItem.Type;
import vm2gol_v2.type.NodeList;
import static vm2gol_v2.util.Utils.invalidType;
import static vm2gol_v2.util.Utils.notYetImpl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class Json {

    private static final String LF = "\n";
//    private static final String BS = "\\";
    private static final String DQ = "\"";

    private static final String INDENT_SPACES = "  ";

    public static String toJson(NodeList tree) {
        return listToJson(tree, 0);
    }

    static String listToJson(NodeList list, int lv) {
        int nextLv = lv + 1;
        String s = "";
        s += "[" + LF;

        int cnt = -1;
        for (NodeItem item : list.getList()) {
            cnt++;

            if (item.type == Type.LIST) {
                s += listToJson(item.getItems(), nextLv);
            } else {
                s += INDENT_SPACES;
                s += toJson(item, nextLv);
            }

            if (cnt < list.size() - 1) {
                s += "," + LF;
            }
        }
        
        s += LF;
        s += "]" + LF;

        return indent(s, lv);
    }

    public static String toJson(NodeItem item, int lv) {
        int nextLv = lv + 0;

        if (item.type == Type.STR) {
            return DQ + item.getStrVal() + DQ;
        } else if (item.type == Type.INT) {
            return String.valueOf(item.getIntVal());
        } else if (item.type == Type.LIST) {
            return listToJson(item.getItems(), nextLv);
        } else {
            throw invalidType(item);
        }
    }

    private static String indent(String s, int lv) {
        String[] lines = StringUtils.split(s, LF);
        List<String> lines2 = new ArrayList<>();

        String spaces = "";
        if (lv >= 1) {
            spaces = INDENT_SPACES;
        }
        
        for (String line : lines) {
            lines2.add(
                    spaces + line
            );
        }
        return String.join(LF, lines2);
    }

    static class ParseResult {
        NodeList nodeList;
        int size;

        ParseResult(NodeList nodeList, int pos){
            this.nodeList = nodeList;
            this.size = pos;
        }
    }

    
    public static NodeList parse(String json) {
        return _parse(json).nodeList;
    }

    
    public static ParseResult _parse(String json) {
        int pos = 1;
        NodeList xs = new NodeList();

        Regex re = new Regex();

        while (pos <= json.length()) {
            String rest = json.substring(pos);
            
            if (rest.startsWith("[")) {
                ParseResult pr = _parse(rest);
                xs.add(pr.nodeList);
                pos += pr.size;
            } else if (rest.startsWith("]")) {
                pos++;
                break;
            } else if (
                    rest.startsWith(" ") ||
                    rest.startsWith(LF) ||
                    rest.startsWith(",")
                    )
            {
                pos++;
            } else if (re.match("^(-?[0-9]+)", rest)) {
                String str = re.group(1);
                int n = Integer.valueOf(str);
                xs.add(NodeItem.of(n));
                pos += str.length();
            } else if (re.match("^\"(.*?)\"", rest)) {
                String str = re.group(1);
                xs.add(NodeItem.of(str));
                pos += str.length() + 2;
            } else {
                throw notYetImpl("must not happen");
            }
        }

        return new ParseResult(xs, pos);
    }

}
