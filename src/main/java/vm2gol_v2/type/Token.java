package vm2gol_v2.type;

import vm2gol_v2.util.Utils;

import static vm2gol_v2.util.Utils.invalidType;

public class Token {

    public enum Type {
        INT("int"),
        STR("str"),
        KW("kw"),
        SYM("sym"),
        IDENT("ident");

        private String str;
        
        Type(String s){
            this.str = s;
        }

        public String getStr() {
            return this.str;
        }

        public static Type of(String str) {
            for (Type type : Type.values()) {
                if (Utils.strEq(type.getStr(), str)) {
                    return type;
                }
            }
            throw new IllegalArgumentException(str);
        }
    }
    
    public Type type;
    private String str;

    public Token(Type type, String val) {
        this.type = type;
        this.str = val;
    }

    public int getIntVal() {
        if (this.type != Type.INT) {
            throw invalidType(this);
        }

        return Integer.valueOf(this.str);
    }

    public String getStr() {
        return this.str;
    }
    
    public String toString() {
        return Utils.toString(this);
    }

    public boolean strEq(String str) {
        if (this.type != Type.SYM) {
            throw invalidType(this);
        }

        return Utils.strEq(this.str, str);
    }

    public boolean is(Type type, String str) {
        return this.type == type && Utils.strEq(this.str, str);
    }

    public static Token fromLine(String line) {
        int i = line.indexOf(":");
        String typeStr = line.substring(0, i);
        String val = line.substring(i + 1);

        return new Token(Type.of(typeStr), val);
    }

    public String toLine() {
        return this.type.getStr() + ":" + getStr();
    }

}
