package vm2gol_v2.type;

import vm2gol_v2.util.Utils;

import static vm2gol_v2.util.Utils.invalidKind;

import vm2gol_v2.util.Json;

public class Token {

    public enum Kind {
        INT("int"),
        STR("str"),
        KW("kw"),
        SYM("sym"),
        IDENT("ident");

        private String str;

        Kind(String s){
            this.str = s;
        }

        public String getStr() {
            return this.str;
        }

        public static Kind of(String str) {
            for (Kind kind : Kind.values()) {
                if (Utils.strEq(kind.getStr(), str)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException(str);
        }
    }

    private int lineNo;
    public Kind kind;
    private String str;

    public Token(int lineNo, Kind kind, String val) {
        this.lineNo = lineNo;
        this.kind = kind;
        this.str = val;
    }

    public int getIntVal() {
        if (this.kind != Kind.INT) {
            throw invalidKind(this);
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
        if (this.kind != Kind.SYM) {
            throw invalidKind(this);
        }

        return Utils.strEq(this.str, str);
    }

    public boolean is(Kind kind, String str) {
        return this.kind == kind && Utils.strEq(this.str, str);
    }

    public static Token fromLine(String line) {
        NodeList list = Json.parse(line);
        return new Token(
                list.getInt(0),
                Kind.of(list.getStr(1)),
                list.getStr(2)
        );
    }

    public String toLine() {
        return String.format(
                "[%d, \"%s\", \"%s\"]",
                this.lineNo,
                this.kind.getStr(),
                getStr()
                );
    }

}
