package sequential;

import util.Constants;
import util.LogLevel;
import util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Logger.log("sequential.Main class started", LogLevel.Success);
        String[] videos = {Constants.VIDEO_PATH1, Constants.VIDEO_PATH2, Constants.VIDEO_PATH3};
        Random rand= new Random();
        int randomIndex = rand.nextInt(videos.length);
        String inputPath = videos[randomIndex];

        handleProcessing(inputPath, Constants.MIDWAY_POINT);
        Logger.log("Processing complete, video is at location: "+Constants.OUTPUT_FOLDER, LogLevel.Success);

    }

    private static boolean validatePaths(String inputPath, String outputPath) {
        File inputFile = new File(inputPath);
        File outputDir = new File(outputPath);

        if (!inputFile.isFile()) {
            Logger.log("Input path is not a valid file: " + inputPath, LogLevel.Error);
            return false;
        }

        if (!outputDir.isDirectory()) {
            Logger.log("Output path is not a valid directory: " + outputPath, LogLevel.Error);
            return false;
        }

        return true;
    }

    private static void handleProcessing(String inputPath, String outputPath) {
        Logger.log("Processing in sequential mode", LogLevel.Status);
        util.VideoProcessing vp = new util.VideoProcessing();

        try {
            vp.extractFrames(inputPath, outputPath, Constants.FPS);
            Logger.log("Video split successfully", LogLevel.Info);

            SequentialProcessor sp = new SequentialProcessor();
            sp.processFramesS(outputPath, Constants.OUTPUT_VIDEO_PATH, Constants.FPS);
            Logger.log("Video processed successfully", LogLevel.Success);

        } catch (IOException | InterruptedException e) {
            Logger.log("Error during processing: " + e.getMessage(), LogLevel.Error);
            throw new RuntimeException(e);
        }
    }
}
