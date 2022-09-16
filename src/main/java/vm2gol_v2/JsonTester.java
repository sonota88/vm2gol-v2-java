package vm2gol_v2;

import vm2gol_v2.util.Json;
import vm2gol_v2.util.Utils;
import vm2gol_v2.type.NodeList;

class JsonTester {

    public static void run() {
        new JsonTester().main();
    }

    private void main() {
        String json = Utils.readStdinAll();

        NodeList list = Json.parse(json);

        System.out.println(Json.toJson(list));
    }
}
