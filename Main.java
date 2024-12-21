import util.LogLevel;
import util.Logger;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Logger.log("Main class", LogLevel.Success);
        ProcessBuilder pb = new ProcessBuilder(args);
        Gui gui = new Gui();
        gui.launch();

       /* //test
        VideoProcessing vp = new VideoProcessing();

        try {
            vp.extractFrames("videos/background_video_people_walking.mp4", "vid1_imgs",60);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

       Logger.log("Video split");

        try {
            vp.makeVideo("vid1_imgs","outputVideo/outputVideo.mp4", 60);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Logger.log("Video compiled");*/

    }
}
