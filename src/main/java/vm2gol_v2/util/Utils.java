package vm2gol_v2.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import org.apache.commons.io.IOUtils;

public class Utils {

    public static final String LF = "\n";
    private static final String BS = "\\";
    public static final String DQ = "\"";

    public static void puts_e(Object... os) {
        for (Object o : os) {
            System.err.print(o + LF);
        }
    }

    public static void putskv_e(Object k, Object v) {
        puts_e(String.format("%s (%s)", k, v));
    }

    public static String readStdinAll() {
        try {
            return IOUtils.toString(System.in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String escape(String s) {
        return s
                .replace(BS, BS + BS)
                .replace(DQ, BS + DQ)
                .replace("\b", BS + "b")
                .replace("\f", BS + "f")
                .replace("\n", BS + "n")
                .replace("\r", BS + "r")
                .replace("\t", BS + "t")
                ;
    }

    public static String toString(Object obj) {
        // return ReflectionToStringBuilder.toString(obj, ToStringStyle.JSON_STYLE);
        return ReflectionToStringBuilder.toString(obj, ToStringStyle.SHORT_PREFIX_STYLE);
    }

    private static RuntimeException genericException(String headMsg, Object ...args) {
        String msg = headMsg;
        for (Object arg : args) {
            String str;
            if (arg == null) {
                str = "null";
            } else {
                str = arg.toString();
            }
            msg += " (" + str + ")";
        }
        return new RuntimeException(msg);
    }

    public static String inspect(Object obj) {
        if (obj == null) {
            return "null";
        } else {
            return toString(obj);
        }
    }

    public static boolean strEq(String s1, String s2) {
        return StringUtils.equals(s1, s2);
    }

    public static RuntimeException notYetImpl(Object ...args) {
        return genericException("Not yet implemented", args);
    }

    public static RuntimeException unexpected(Object ...args) {
        return genericException("Something unexpected", args);
    }

    public static RuntimeException unsupported(Object ...args) {
        return genericException("Unsupported case (for now)", args);
    }

    public static RuntimeException invalidType(Object ...args) {
        return genericException("Invalid type", args);
    }

    public static RuntimeException invalidKind(Object ...args) {
        return genericException("Invalid kind", args);
    }

}
