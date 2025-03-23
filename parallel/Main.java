package parallel;

import util.Constants;
import util.LogLevel;
import util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Main {public static void main(String[] args) {
    Logger.log("sequential.Main class started", LogLevel.Success);
    Logger.log("Enter the path of the video file", LogLevel.Info);
    Scanner scanner = new Scanner(System.in);
    String inputPath = scanner.nextLine().trim();
    if(isValidVideoFile(inputPath)) {
        Logger.log("Valid file", LogLevel.Success);
    } else {
        return;
    }

    handleProcessing(inputPath, Constants.MIDWAY_POINT);
    Logger.log("Processing complete", LogLevel.Success);

}

    private static boolean isValidVideoFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            Logger.log("File does not exist or is not a valid file", LogLevel.Error);
            return false;
        }

        if (path.toLowerCase().endsWith(".mp4")) {
            return true;
        }

        Logger.log("Unsupported file format", LogLevel.Error);
        return false;
    }


    private static void handleProcessing(String inputPath, String outputPath) {
        Logger.log("Processing in sequential mode", LogLevel.Status);
        util.VideoProcessing vp = new util.VideoProcessing();

        try {
            vp.extractFrames(inputPath, outputPath, Constants.FPS);
            Logger.log("Video split successfully", LogLevel.Info);

            ParallelProcessor sp = new ParallelProcessor();
            sp.processFramesP(outputPath, Constants.OUTPUT_VIDEO_PATH, Constants.FPS);
            Logger.log("Video processed successfully", LogLevel.Success);

        } catch (IOException | InterruptedException e) {
            Logger.log("Error during processing: " + e.getMessage(), LogLevel.Error);
            throw new RuntimeException(e);
        }
    }
}
