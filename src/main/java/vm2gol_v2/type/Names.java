package vm2gol_v2.type;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Names {

    private List<String> names;

    public Names () {
        this.names = new ArrayList<>();
    }

    public Names (List<String> names) {
        this.names = names;
    }

    public static Names fromNodeList(NodeList items) {
        List<String> names = items.getList().stream()
                .map((NodeItem item)-> item.getStrVal())
                .collect(Collectors.toList())
                ;

        return new Names(names);
    }

    public void add(String name) {
        this.names.add(name);
    }

    public boolean contains(String name) {
        return this.names.contains(name);
    }

    public int indexOf(String name) {
        return this.names.indexOf(name);
    }

}
