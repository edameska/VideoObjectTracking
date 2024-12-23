import sequential.SequentialProcessor;
import util.LogLevel;
import util.Logger;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Logger.log("Main class", LogLevel.Success);
        ProcessBuilder pb = new ProcessBuilder(args);
        Gui gui = new Gui();
        //gui.launch();

        //test
        util.VideoProcessing vp = new util.VideoProcessing();

        try {
            vp.extractFrames("videos/cars1.mp4", "vid2_imgs",60);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

      Logger.log("Video split");


        SequentialProcessor sp = new SequentialProcessor();
        try {
            sp.processFramesS("vid2_imgs", "outputVideo", 60);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Logger.log("Video processed");

    }
}
