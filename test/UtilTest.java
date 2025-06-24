package test;

import util.Constants;
import util.VideoProcessing;

public class UtilTest {
    private static final String INPUT_VIDEO_PATH = "/home/edameska/Downloads/89221002_VideoObjectTracking/videos/fps120.mp4";
    private static final String FRAMES_OUTPUT_FOLDER = "midway_test";
    private static final String OUTPUT_VIDEO_PATH = "out_test.mp4";
    private static final int FPS = 30;

    public static void main(String[] args) {
        VideoProcessing vp = new VideoProcessing();

        try {
            long start = System.currentTimeMillis();
            vp.extractFrames(INPUT_VIDEO_PATH, FRAMES_OUTPUT_FOLDER, FPS);
            long extractTime = System.currentTimeMillis() - start;
            System.out.println("extractFrames took: " + extractTime + " ms");

            start = System.currentTimeMillis();
            vp.makeVideo(FRAMES_OUTPUT_FOLDER, OUTPUT_VIDEO_PATH, FPS);
            long makeVideoTime = System.currentTimeMillis() - start;
            System.out.println("makeVideo took: " + makeVideoTime + " ms");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
