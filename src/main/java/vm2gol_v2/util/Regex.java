package vm2gol_v2.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Regex {

    private Matcher matcher;

    public boolean match(String pattern, String str) {
        Pattern p = Pattern.compile(pattern);
        matcher = p.matcher(str);
        return matcher.find();
    }

    public String group(int group) {
        return matcher.group(group);
    }

}
