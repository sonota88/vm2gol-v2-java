package vm2gol_v2.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import vm2gol_v2.util.Utils;

public class NodeList {

    private List<NodeItem> items = new ArrayList<>();

    public NodeList() {}

    public NodeList(List<NodeItem> items) {
        this.items = items;
    }

    public NodeList addAll(NodeList stmts) {
        this.items.addAll(stmts.items);
        return this;
    }

    public NodeList add(String s) {
        this.add(NodeItem.of(s));
        return this;
    }

    public NodeList add(NodeList nl) {
        this.add(NodeItem.of(nl));
        return this;
    }

    public NodeList add(NodeItem item) {
        this.items.add(item);
        return this;
    }

    public NodeItem first() {
        return items.get(0);
    }

    public NodeList rest() {
        return new NodeList(
                items.subList(1, items.size())
                );
    }

    public NodeItem get(int index) {
        return this.items.get(index);
    }

    public int size() {
        return items.size();
    }

    public List<NodeItem> getList() {
        return this.items;
    }

    public NodeList reverse() {
        List<NodeItem> newItems = new ArrayList<>(getList());
        Collections.copy(newItems, getList());
        Collections.reverse(newItems);
        return new NodeList(newItems);
    }

    public static NodeList empty() {
        return new NodeList();
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    @Override
    public String toString() {
        return Utils.toString(this);
    }

    public String getStr(int i) {
        return this.get(i).getStrVal();
    }

}
