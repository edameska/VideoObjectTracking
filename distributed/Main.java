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
// didnt work because scanner is not supported in distributed mode
        /*// Only ask for input on rank 0 (the master process)
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
        }*/

        String inputPath = null;
        if (rank == 0) {
            inputPath = args[3]; // get from command line, first 3 args are for the mpi
            if (isValidVideoFile(inputPath)) {
                Logger.log("Valid file", LogLevel.Success);
            } else {
                Logger.log("Invalid file. Exiting.", LogLevel.Error);
                MPI.Finalize();
                return;}
            Logger.log("distributed.Main input path: " + inputPath, LogLevel.Success);
        }
        byte[] pathBytes = new byte[256];
        if (rank == 0) {
            byte[] tmp = inputPath.getBytes();
            System.arraycopy(tmp, 0, pathBytes, 0, tmp.length);
        }
        long start = System.currentTimeMillis();
        MPI.COMM_WORLD.Bcast(pathBytes, 0, pathBytes.length, MPI.BYTE, 0);
        inputPath = new String(pathBytes).trim();


        handleProcessing(inputPath, Constants.MIDWAY_POINT, rank);

        Logger.log("Processing complete", LogLevel.Success);
        Logger.log("Time taken: " + (System.currentTimeMillis() - start) + " ms", LogLevel.Info);
       // MPI.Finalize();
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

    private static void handleProcessing(String inputPath, String outputPath, int rank) {
        Logger.log("Processing in distributed mode", LogLevel.Status);
        util.VideoProcessing vp = new util.VideoProcessing();

        try {
            if(rank==0){
            vp.extractFrames(inputPath, outputPath, Constants.FPS);
            Logger.log("Video split successfully", LogLevel.Info);
            }

            DistributedProcessor dp = new DistributedProcessor();
            dp.processFramesD(outputPath, Constants.OUTPUT_VIDEO_PATH, Constants.FPS);
            Logger.log("Video processed successfully", LogLevel.Success);

        } catch (IOException | InterruptedException e) {
            Logger.log("Error during processing: " + e.getMessage(), LogLevel.Error);
            throw new RuntimeException(e);
        }
    }
}
