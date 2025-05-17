package distributed;

import mpi.MPI;
import mpi.MPIException;
import util.Constants;
import util.LogLevel;
import util.Logger;
import util.VideoProcessing;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class DistributedProcessor {

    public void processFramesD(String imgPath, String outputPath, int fps) throws IOException, InterruptedException, MPIException {
        // Initialize MPI
        MPI.Init(new String[0]);

        try {
            int rank = MPI.COMM_WORLD.Rank();
            int size = MPI.COMM_WORLD.Size();

            File inputDir = new File(imgPath);
            File outputDir = new File(outputPath);

            // Only rank 0 handles directory validation and frame listing
            File[] frames = null;
            if (rank == 0) {
                frames = inputDir.listFiles(((dir, name) -> name.endsWith(".png")));
                if (frames == null || frames.length == 0) {
                    Logger.log("No frames found in the input directory", LogLevel.Error);
                    MPI.COMM_WORLD.Bcast(new int[]{0}, 0, 1, MPI.INT, 0); // Signal to abort
                    return;
                }
                Arrays.sort(frames, Comparator.comparingInt(f -> Integer.parseInt(f.getName().replaceAll("\\D+", ""))));

                // Prepare output directory
                if (outputDir.exists()) {
                    File[] outputFiles = outputDir.listFiles();
                    if (outputFiles != null && outputFiles.length > 0) {
                        for (File file : outputFiles) {
                            if (!file.delete()) {
                                Logger.log("Failed to delete file: " + file.getName(), LogLevel.Error);
                            }
                        }
                    }
                } else {
                    outputDir.mkdirs();
                }

                // Broadcast number of frames to all processes
                int[] frameCount = new int[]{frames.length};
                MPI.COMM_WORLD.Bcast(frameCount, 0, 1, MPI.INT, 0);
            } else {
                // Receive frame count from rank 0
                int[] frameCount = new int[1];
                MPI.COMM_WORLD.Bcast(frameCount, 0, 1, MPI.INT, 0);
                if (frameCount[0] == 0) return; // Abort if no frames
            }

            // Get total frame count (broadcasted from rank 0)
            int[] frameCount = new int[1];
            MPI.COMM_WORLD.Bcast(frameCount, 0, 1, MPI.INT, 0);
            int numFrames = frameCount[0];

            // Divide work among processes
            int framesPerProcess = numFrames / size;
            int remainder = numFrames % size;

            int startFrame = rank * framesPerProcess + Math.min(rank, remainder);
            int endFrame = startFrame + framesPerProcess + (rank < remainder ? 1 : 0);

            Logger.log("Process " + rank + " will process frames from " + startFrame + " to " + (endFrame - 1), LogLevel.Debug);

            // Process assigned frames
            if (numFrames > 0) {
                processFrameRange(imgPath, outputPath, startFrame, endFrame);
            }

            // Synchronize all processes
            MPI.COMM_WORLD.Barrier();

            // Rank 0 handles video creation
            if (rank == 0) {
                VideoProcessing vp = new VideoProcessing();
                vp.makeVideo(outputPath, "output.mp4", fps);
                Logger.log("Processing complete in parallel", LogLevel.Status);
            }
        } finally {
            MPI.Finalize();
        }
    }

    private void processFrameRange(String imgPath, String outputPath, int startFrame, int endFrame)
            throws IOException, InterruptedException {
        File inputDir = new File(imgPath);
        File[] frames = inputDir.listFiles(((dir, name) -> name.endsWith(".png")));
        Arrays.sort(frames, Comparator.comparingInt(f -> Integer.parseInt(f.getName().replaceAll("\\D+", ""))));

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        BufferedImage prevFrame = null;

        // Load the first frame if this process isn't starting at frame 0
        if (startFrame > 0) {
            prevFrame = ImageIO.read(frames[startFrame - 1]);
        }

        for (int i = startFrame; i < endFrame && i < frames.length - 1; i++) {
            File frameFile1 = frames[i];
            File frameFile2 = frames[i + 1];

            BufferedImage currentFrame = ImageIO.read(frameFile2);

            if (prevFrame != null) {
                BufferedImage finalPrevFrame = prevFrame;
                executor.submit(() -> {
                    try {
                        Logger.log("Processing frame: " + frameFile2.getName(), LogLevel.Debug);
                        BufferedImage diffFrame = computeDifference(finalPrevFrame, currentFrame);
                        saveProccessedFrame(diffFrame, outputPath, frameFile2.getName());
                    } catch (IOException e) {
                        Logger.log("Error processing frame: " + frameFile2.getName(), LogLevel.Error);
                    }
                });
            }
            prevFrame = currentFrame;
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
    }

    private void saveProccessedFrame(BufferedImage frame, String output, String frameName) throws IOException {
        File outputFile = new File(output, frameName);
        ImageIO.write(frame, "PNG", outputFile);

    }

    private BufferedImage computeDifference(BufferedImage prevFrame, BufferedImage currentFrame){
        int width=prevFrame.getWidth();
        int height=prevFrame.getHeight();

        boolean [][] visited = new boolean[width][height];
        Color[] colors=randomColors(10);
        int colorIndex=0; //index of the color to use for coloring the contiguous area

        BufferedImage diffFrame=new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if(!visited[i][j]) {

                    int prevPixel = prevFrame.getRGB(i, j);
                    int currPixel = currentFrame.getRGB(i, j);
                    double diff = pixelDifference(prevPixel, currPixel);


                    // check if the pixel has changed significantly
                    //Logger.log("Pixel difference at " + i + ", " + j + ": " + pixelDifference(prevPixel, currPixel), LogLevel.Debug);

                    if (diff > Constants.PIXEL_DIFF_THRESHOLD) {

                        // recolor pixel if difference

                        //diffFrame.setRGB(i, j, new Color(255, 0, 0, 40).getRGB());
                        fillRegion(diffFrame, prevFrame, currentFrame, visited, i, j, colors[colorIndex]);
                        colorIndex = (colorIndex + 1) % colors.length;//cycle through colors
                        // Logger.log("Pixel changed at: "+i+" "+j, LogLevel.Debug);
                    } else {
                        //keep if no difference
                        diffFrame.setRGB(i, j, currPixel);
                    }
                }
            }

        }
        return diffFrame;
    }

    private void fillRegion(BufferedImage diffFrame, BufferedImage prevFrame, BufferedImage currentFrame, boolean[][] visited, int x, int y, Color color) {
        int width = prevFrame.getWidth();
        int height = prevFrame.getHeight();

        Stack<Point> stack = new Stack<>();
        stack.push(new Point(x, y));

        while (!stack.isEmpty()) {
            Point point = stack.pop();
            int px = point.x;
            int py = point.y;

            //chheck bounds and already visited
            if (px < 0 || px >= width || py < 0 || py >= height || visited[px][py]) {
                continue;
            }

            int prevPixel = prevFrame.getRGB(px, py);
            int currPixel = currentFrame.getRGB(px, py);
            double diff = pixelDifference(prevPixel, currPixel);

            // if pixel difference significant, color it and add neighbors to stack
            if (diff > Constants.PIXEL_DIFF_THRESHOLD) {
                visited[px][py] = true;
                diffFrame.setRGB(px, py, color.getRGB());

                stack.push(new Point(px + 1, py)); //right
                stack.push(new Point(px - 1, py)); // left
                stack.push(new Point(px, py + 1)); //up
                stack.push(new Point(px, py - 1)); //down
            }
        }
    }


    private Color[] randomColors(int n) {
        Color[] colors = new Color[n];
        for (int i = 0; i < n; i++) {
            int r = (int) (Math.random() * 256);
            int g = (int) (Math.random() * 256);
            int b = (int) (Math.random() * 256);
            int alpha = (int) (Math.random() * 50);

            colors[i] = new Color(r, g, b, alpha);
        }
        return colors;
    }



    private double pixelDifference(int prevPixel, int currPixel) {
        //rgb structure: AARRGGBB in bytes
        int r1 = (prevPixel >> 16) & 0xff;//shift 16 bits to the right and mask with 0xff
        int g1 = (prevPixel >> 8) & 0xff;
        int b1 = prevPixel & 0xff;

        int r2 = (currPixel >> 16) & 0xff;
        int g2 = (currPixel >> 8) & 0xff;
        int b2 = currPixel & 0xff;


        int diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);

        // 255 is the maximum value for a color channel

        return (diff / (3.0 * 255.0)) * 100.0; // Normalize and scale to percentage
    }
}
