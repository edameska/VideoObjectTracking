package distributed;

import mpi.MPI;
import mpi.MPIException;
import util.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

public class DistributedProcessor {
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks for sending bytes so we limit overhead but don't overload the network/buffer overflow
    public void processFramesD(String imgPath, String outputPath, int fps) throws IOException, MPIException, InterruptedException {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        long startTime =System.currentTimeMillis();


        if (rank == 0) {
            String[] filenames = null;
            int[] count = new int[1];
            // Setup output directory
            File outputDir = new File(outputPath);
            if (outputDir.exists()) deleteRecursively(outputDir);
            outputDir.mkdirs();

            // Load all image filenames
            File[] files = new File(imgPath).listFiles((d, name) -> name.endsWith(".png"));
            if (files == null || files.length == 0) {
                Logger.log("No frames found.", LogLevel.Error);
                count[0] = 0;
                MPI.COMM_WORLD.Bcast(count, 0, 1, MPI.INT, 0);
                return;
            }
            Arrays.sort(files, Comparator.comparingInt(f -> Integer.parseInt(f.getName().replaceAll("\\D", ""))));
            filenames = Arrays.stream(files).map(File::getName).toArray(String[]::new);
            int totalFrames = filenames.length;
            count[0] = totalFrames;
            MPI.COMM_WORLD.Bcast(count, 0, 1, MPI.INT, 0);

            // Send assigned frames to each rank
            for (int r = 1; r < size; r++) {
                int[] range = computeWorkRange(r, size, totalFrames);
                int start = range[0];
                int end = range[1];
                for (int i = start; i < end; i++) {
                    byte[] imgBytes = frameToBytes(loadSingleFrame(imgPath, filenames[i]));
                    sendChunkedBytes(imgBytes, r, 1000 + i); //tag so the node can identify the frame
                }
            }

            // Process own chunk
            int[] myRange = computeWorkRange(rank, size, totalFrames);
            int myStart = myRange[0];
            int myEnd = myRange[1];
            List<byte[]> myDiffs = processLocalChunk(filenames, myStart, myEnd, imgPath);

            // Save own diffs
            for (int i = 0; i < myDiffs.size(); i++) {
                try (FileOutputStream fos = new FileOutputStream(new File(outputPath, filenames[i + 1]))) {
                    fos.write(myDiffs.get(i));
                }
            }

            // Receive diffs from other ranks

                for (int r = 1; r < size; r++) {
                    int[] range = computeWorkRange(r, size, totalFrames);
                    int start = range[0];
                    int end = range[1];
                    for (int i = start + 1; i < end; i++) {
                        if (MPI.COMM_WORLD.Iprobe(r, 2000 + i)!=null) {
                            Logger.log("Receiving diff for frame " + (i + 1) + " from rank " + r, LogLevel.Debug);
                            byte[] diffBytes = recvChunkedBytes(r, 2000 + i);
                            Logger.log("Received diff for frame " + (i + 1) + " from rank " + r, LogLevel.Debug);
                            try (FileOutputStream fos = new FileOutputStream(new File(outputPath, filenames[i]))) {
                                fos.write(diffBytes);
                            }
                    }
                }
            }



            new VideoProcessing().makeVideo(outputPath, "output.mp4", fps);
            Logger.log("Distributed processing complete.", LogLevel.Status);

        } else {
            List<BufferedImage> diffs = new ArrayList<>();
            int[] count = new int[1];
            MPI.COMM_WORLD.Bcast(count, 0, 1, MPI.INT, 0);
            int totalFrames = count[0];

            int[] range = computeWorkRange(rank, size, totalFrames);
            Logger.log("Rank " + rank + " processing frames from " + range[0] + " to " + range[1], LogLevel.Debug);
            int start = range[0];
            int end = range[1];
            List<byte[]> diffBytesList = new ArrayList<>();

            BufferedImage prevFrame = null;

            for (int i = start; i < end; i++) {
                byte[] imgBytes = recvChunkedBytes(0, 1000 + i);
                Logger.log("Rank " + rank + " received frame: " + (i + 1), LogLevel.Debug);
                BufferedImage currFrame = ImageIO.read(new ByteArrayInputStream(imgBytes));
                Logger.log("Rank " + rank + " processing frame: " + (i + 1), LogLevel.Debug);

                if (currFrame == null) {
                    Logger.log("ImageIO failed to decode image from rank " + rank, LogLevel.Error);
                    continue;
                }

                if (prevFrame != null) {
                    BufferedImage diff = computeDifference(prevFrame, currFrame);
                    byte[] diffBytes = frameToBytes(diff);
                    diffBytesList.add(diffBytes);  // store compressed image only
                    // Store for later
                }

                prevFrame = currFrame;
            }

            Logger.log("Rank " + rank + " finished processing. Sending diffs...", LogLevel.Info);

            // Now send all diffs at once
            for (int i = 1; i < diffBytesList.size() + 1; i++) {
                sendChunkedBytes(diffBytesList.get(i - 1), 0, 2000 + (start + i));
            }


            Logger.log("Rank " + rank + " done sending all diffs", LogLevel.Info);
        }

        long endTime = System.currentTimeMillis();
        Logger.log("Rank " + rank + " processing time: " + (endTime - startTime) + " ms", LogLevel.Status);
        MPI.Finalize();
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                deleteRecursively(sub);
            }
        }
        if (!file.delete()) {
            Logger.log("Failed to delete file: " + file.getAbsolutePath(), LogLevel.Error);
        }
    }
    private byte[] frameToBytes(BufferedImage img) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        }
    }

    private List<byte[]> processLocalChunk(String[] filenames, int start, int end, String path) throws IOException {
        List<byte[]> diffs = new ArrayList<>();
        BufferedImage prev = loadSingleFrame(path, filenames[start]);
        for (int i = start + 1; i < end; i++) {
            BufferedImage curr = loadSingleFrame(path, filenames[i]);
            BufferedImage diff = computeDifference(prev, curr);
            diffs.add(frameToBytes(diff));
            prev = curr;
        }
        return diffs;
    }
    private void sendChunkedBytes(byte[] data, int dest, int tagBase) throws MPIException {
        Logger.log("Entered sendChunckedBytes");
        int totalLen = data.length;
        int chunks = (totalLen + CHUNK_SIZE - 1) / CHUNK_SIZE;

        // Send metadata (blocking)
        MPI.COMM_WORLD.Send(new int[]{totalLen, chunks}, 0, 2, MPI.INT, dest, tagBase);

        // Prepare request array
        mpi.Request[] requests = new mpi.Request[chunks];
        // Post all chunk sends non-blocking
        for (int i = 0; i < chunks; i++) {
            int start = i * CHUNK_SIZE;
            int len = Math.min(CHUNK_SIZE, totalLen - start);
            Logger.log("Sending chunk " + i + " of " + chunks, LogLevel.Debug);
            requests[i] = MPI.COMM_WORLD.Isend(data, start, len, MPI.BYTE, dest, tagBase + 1 + i);
        }

        // Wait for all sends to complete
        mpi.Request.Waitall(requests);
    }

    private byte[] recvChunkedBytes(int source, int tagBase) throws MPIException {
        Logger.log("Entered recvChunckedBytes");
        int[] meta = new int[2];

        // Receive metadata (blocking)
        MPI.COMM_WORLD.Recv(meta, 0, 2, MPI.INT, source, tagBase);
        int totalLen = meta[0], chunks = meta[1];
        byte[] data = new byte[totalLen];

        // Prepare request array
        mpi.Request[] requests = new mpi.Request[chunks];
        // Post all chunk receives non-blocking
        int offset = 0;
        for (int i = 0; i < chunks; i++) {
            int len = Math.min(CHUNK_SIZE, totalLen - offset);
            Logger.log("Receiving chunk " + i + " of " + chunks, LogLevel.Debug);
            requests[i] = MPI.COMM_WORLD.Irecv(data, offset, len, MPI.BYTE, source, tagBase + 1 + i);
            offset += len;
        }
        //wait for all receives to complete
        mpi.Request.Waitall(requests);

        return data;
    }



        private int[] computeWorkRange(int rank, int size, int total) {
            int base = total / size;
            int extra = total % size;

            int start = rank * base + Math.min(rank, extra);
            int end = start + base + (rank < extra ? 1 : 0);
            int readStart = (rank == 0) ? start : start - 1; // include previous frame for diff except for rank 0

            return new int[]{readStart, end, start};
        }

        private BufferedImage loadSingleFrame(String path, String filename) {
            File imgFile = new File(path, filename);
            BufferedImage img = null;
            try {
                img = ImageIO.read(imgFile);
                if (img == null) {
                    Logger.log("Failed to read image: " + filename, LogLevel.Warn);
                }
            } catch (IOException e) {
                Logger.log("IO error reading image " + filename + ": " + e.getMessage(), LogLevel.Error);
            } catch (OutOfMemoryError e) {
                Logger.log("Out of memory loading image " + filename, LogLevel.Error);
                System.gc();
            }
            return img;
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


