import util.LogLevel;
import util.Logger;

public class Main {
    public static void main(String[] args) {
        Logger.log("Main class", LogLevel.Success);
        //ProcessBuilder pb = new ProcessBuilder(args);
        Gui gui = new Gui();
        gui.launch();


    }
}
