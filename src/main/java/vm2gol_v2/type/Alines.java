package vm2gol_v2.type;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembly lines
 */
public class Alines {
    
    private List<String> lines;

    public Alines() {
        this.lines = new ArrayList<>();
    }
    
    public void add(String line, Object ...args) {
        lines.add(
                String.format(line, args)
                );
    }

    public List<String> getLines() {
        return this.lines;
    }

    public void addAll(Alines alines) {
        this.lines.addAll(alines.getLines());
    }

}
