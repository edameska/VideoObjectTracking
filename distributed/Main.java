package distributed;

import util.Constants;
import util.LogLevel;
import util.Logger;
import mpi.*;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws MPIException {
        // Initialize MPJ
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank(); // Get the rank of the current process
        int size = MPI.COMM_WORLD.Size(); // Get the total number of processes

        Logger.log("distributed.Main class started with " + size + " processes", LogLevel.Success);

        // Only ask for input on rank 0 (the master process)
        String inputPath = null;
        if (rank == 0) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter the path of the video file:");
            inputPath = scanner.nextLine().trim();
            if (isValidVideoFile(inputPath)) {
                Logger.log("Valid file", LogLevel.Success);
            } else {
                Logger.log("Invalid file. Exiting.", LogLevel.Error);
                MPI.Finalize();
                return;
            }
        }

        // Convert the String inputPath to a char array for broadcasting
        char[] inputPathChars = new char[0];
        if (rank == 0) {
            inputPathChars = inputPath.toCharArray();
        }

        // Broadcast the char array
        MPI.COMM_WORLD.Bcast(inputPathChars, 0, inputPathChars.length, MPI.CHAR, 0);
        inputPath = new String(inputPathChars);

        handleProcessing(inputPath, Constants.MIDWAY_POINT);

        Logger.log("Processing complete", LogLevel.Success);
        MPI.Finalize();
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
        Logger.log("Processing in distributed mode", LogLevel.Status);
        util.VideoProcessing vp = new util.VideoProcessing();

        try {
            vp.extractFrames(inputPath, outputPath, Constants.FPS);
            Logger.log("Video split successfully", LogLevel.Info);

            DistributedProcessor dp = new DistributedProcessor();
            dp.processFramesD(outputPath, Constants.OUTPUT_VIDEO_PATH, Constants.FPS);
            Logger.log("Video processed successfully", LogLevel.Success);

        } catch (IOException | InterruptedException e) {
            Logger.log("Error during processing: " + e.getMessage(), LogLevel.Error);
            throw new RuntimeException(e);
        }
    }
}
