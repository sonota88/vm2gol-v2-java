package vm2gol_v2.type;

import vm2gol_v2.util.Json;
import vm2gol_v2.util.Utils;
import static vm2gol_v2.util.Utils.invalidType;
import static vm2gol_v2.util.Utils.DQ;

public class NodeItem {

    public enum Type {
        INT, STR, LIST;
    }

    public Type type;
    private Integer intVal;
    private String strVal;
    private NodeList items;

    public NodeItem(String s) {
        this.type = Type.STR;
        this.strVal = s;
    }

    public NodeItem(NodeList nl) {
        this.type = Type.LIST;
        this.items = nl;
    }

    public NodeItem(Integer n) {
        this.type = Type.INT;
        this.intVal = n;
    }

    public static NodeItem of(String s) {
        return new NodeItem(s);
    }

    public static NodeItem of(int n) {
        return new NodeItem(n);
    }

    public static NodeItem of(NodeList nl) {
        return new NodeItem(nl);
    }

    public String getStrVal() {
        if (this.type != Type.STR) {
            throw invalidType(this);
        }
        return this.strVal;
    }

    public int getIntVal() {
        if (this.type != Type.INT) {
            throw invalidType(this);
        }
        return this.intVal;
    }

    public NodeList getItems() {
        if (this.type != Type.LIST) {
            throw invalidType(this);
        }
        return this.items;
    }

    @Override
    public String toString() {
        switch (this.type) {
        case STR:
            return DQ + this.strVal + DQ;
        case INT: 
            return Utils.toString(this.intVal);
        case LIST: 
            return Json.toJson(items);
        default:
            throw new RuntimeException("must not happen");
        }
    }

    public boolean strEq(String str) {
        if (this.type != Type.STR) {
            throw invalidType(this);
        }
        return Utils.strEq(str, this.strVal);
    }

    public String inspect() {
        switch (this.type) {
        case INT:
            return String.valueOf(this.intVal);
        case STR:
            return DQ + this.strVal + DQ;
        case LIST:
            return inspectList();
        default:
            throw invalidType(this.type);
        }
    }

    private String inspectList() {
        String s = "";
        s += "[";

        for (int i=0; i<this.items.size(); i++) {
            NodeItem item = this.items.get(i);
            if (i >= 1) {
                s += ", ";
            }
            s += item.inspect();
        }

        s += "]";
        return s;
    }

}
