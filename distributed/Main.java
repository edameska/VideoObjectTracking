package distributed;

import mpi.MPI;
import util.Constants;
import util.LogLevel;
import util.Logger;
import util.VideoProcessing;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        MPI.Init(args);
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        Logger.log("Main started with " + size + " processes", LogLevel.Success);

        String inputPath = args[3];

        if (rank == 0) {
            if (!inputPath.endsWith(".mp4") || !new File(inputPath).exists()) {
                Logger.log("Invalid input video file. Exiting.", LogLevel.Error);
                MPI.Finalize();
                return;
            }
            Logger.log("Extracting frames from: " + inputPath, LogLevel.Info);
            new VideoProcessing().extractFrames(inputPath, Constants.MIDWAY_POINT, Constants.FPS);
        }

        // All ranks process frames
        new DistributedProcessor().processFramesD(Constants.MIDWAY_POINT, Constants.OUTPUT_VIDEO_PATH, Constants.FPS);
        Logger.log("Processing complete", LogLevel.Success);
        MPI.Finalize();

    }
}
