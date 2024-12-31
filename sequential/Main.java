package sequential;

import util.Constants;
import util.Gui;
import util.LogLevel;
import util.Logger;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Logger.log("sequential.Main class", LogLevel.Success);
        ProcessBuilder pb = new ProcessBuilder(args);
        Gui gui = new Gui();
        gui.launch();

    }
}
