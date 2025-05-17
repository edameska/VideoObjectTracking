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
        try {
            int rank = MPI.COMM_WORLD.Rank();
            int size = MPI.COMM_WORLD.Size();

            File[] frames = null;
            int numFrames;

            if (rank == 0) {
                File inputDir = new File(imgPath);
                frames = inputDir.listFiles((dir, name) -> name.endsWith(".png"));

                if (frames == null || frames.length == 0) {
                    Logger.log("No frames found in the input directory", LogLevel.Error);
                    MPI.COMM_WORLD.Bcast(new int[]{0}, 0, 1, MPI.INT, 0);
                    return;
                }

                Arrays.sort(frames, Comparator.comparingInt(f -> Integer.parseInt(f.getName().replaceAll("\\D+", ""))));

                // Clear or create output directory
                File outputDir = new File(outputPath);
                if (outputDir.exists()) {
                    for (File f : outputDir.listFiles()) f.delete();
                } else {
                    outputDir.mkdirs();
                }
            }

            // Step 1: Broadcast frame count
            int[] frameCount = new int[1];
            if (rank == 0) frameCount[0] = frames.length;
            MPI.COMM_WORLD.Bcast(frameCount, 0, 1, MPI.INT, 0);
            numFrames = frameCount[0];
            if (numFrames == 0) return;

            // Step 2: Distribute filenames from rank 0
            String[] filenames = new String[numFrames];
            if (rank == 0) {
                for (int i = 0; i < numFrames; i++) filenames[i] = frames[i].getName();
            }
            MPI.COMM_WORLD.Bcast(filenames, 0, numFrames, MPI.OBJECT, 0);

            // Step 3: Calculate assigned range with overlap
            int framesPerProcess = numFrames / size;
            int remainder = numFrames % size;

            // Regular range calculation (non-overlapping)
            int regularStart = rank * framesPerProcess + Math.min(rank, remainder);
            int regularEnd = regularStart + framesPerProcess + (rank < remainder ? 1 : 0);

            // Adjusted range with overlap (read one frame before your range if not first process)
            int startFrameRead = (rank > 0) ? regularStart - 1 : regularStart;
            int endFrameRead = regularEnd;

            Logger.log("Process " + rank + " will read frames " + startFrameRead + " to " + (endFrameRead - 1) +
                    " and process " + regularStart + " to " + (regularEnd - 1), LogLevel.Debug);

            // Step 4: Process frames with overlap handling
            processFramesWithOverlap(imgPath, outputPath, filenames, startFrameRead, endFrameRead, regularStart);

            MPI.COMM_WORLD.Barrier();

            if (rank == 0) {
                VideoProcessing vp = new VideoProcessing();
                vp.makeVideo(outputPath, "output.mp4", fps);
                Logger.log("Processing complete in distributed mode", LogLevel.Status);
            }
        } finally {
            MPI.Finalize();
        }
    }


    private void processFramesWithOverlap(String imgPath, String outputPath, String[] filenames,
                                          int startRead, int endRead, int regularStart) throws IOException {
        // Read the first frame
        BufferedImage prevFrame = ImageIO.read(new File(imgPath, filenames[startRead]));

        // Process each subsequent frame
        for (int i = startRead + 1; i < endRead; i++) {
            File currentFrameFile = new File(imgPath, filenames[i]);
            BufferedImage currentFrame = ImageIO.read(currentFrameFile);

            // Only process and save frames that are in our regular (non-overlap) range
            if (i >= regularStart) {
                Logger.log("Processing frame: " + currentFrameFile.getName(), LogLevel.Debug);
                BufferedImage diff = computeDifference(prevFrame, currentFrame);
                saveProccessedFrame(diff, outputPath, currentFrameFile.getName());
            }

            prevFrame = currentFrame;
        }
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
