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
    private static final int CHUNK_SIZE = 64 * 1024; // 64KB chunks for sending bytes

    public void processFramesD(String imgPath, String outputPath, int fps) throws IOException, InterruptedException, MPIException {
        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();

        String[] filenames = distributeFilenames(imgPath, rank);
        if (filenames == null) return;

        if (rank == 0) {
            File outputDir = new File(outputPath);
            if (outputDir.exists()) deleteRecursively(outputDir);
            outputDir.mkdirs();
        }
        MPI.COMM_WORLD.Barrier();

        int totalFrames = filenames.length;
        int[] workRange = computeWorkRange(rank, size, totalFrames);
        int readStart = workRange[0];
        int end = workRange[1];
        int processStart = workRange[2];

        BufferedImage prevFrame = null;
        if (rank > 0 && readStart >= 0) {
            prevFrame = receiveFrame(rank - 1);
        }

        BufferedImage prevLocalFrame = prevFrame;

        // Store all diff bytes locally for ranks > 0
        List<byte[]> localDiffs = new ArrayList<>();

        for (int i = readStart; i < end; i++) {
            BufferedImage currentFrame = loadSingleFrame(imgPath, filenames[i]);
            if (currentFrame == null) continue;

            if (prevLocalFrame != null) {
                BufferedImage diffImg = computeDifference(prevLocalFrame, currentFrame, rank);

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(diffImg, "PNG", baos);
                    byte[] diffBytes = baos.toByteArray();

                    if (rank == 0) {
                        String fname = filenames[processStart + (i - readStart)];
                        try (FileOutputStream fos = new FileOutputStream(new File(outputPath, fname))) {
                            fos.write(diffBytes);
                        }
                    } else {
                        // Save to local list, send later
                        localDiffs.add(diffBytes);
                    }
                }
                diffImg.flush();
            }
            prevLocalFrame = currentFrame;
            currentFrame.flush();
        }

        if (rank < size - 1 && prevLocalFrame != null) {
            sendFrame(prevLocalFrame, rank + 1);
            prevLocalFrame.flush();
        }

        if (rank != 0) {
            // Send count first
            int localDiffCount = localDiffs.size();
            MPI.COMM_WORLD.Send(new int[]{localDiffCount}, 0, 1, MPI.INT, 0, 5000);

            // Send all diffs one by one
            for (int j = 0; j < localDiffCount; j++) {
                sendChunkedBytes(localDiffs.get(j), 0, 2000 + j);
            }
        } else {
            // Rank 0 receives all diffs from other ranks and writes immediately
            for (int src = 1; src < size; src++) {
                int[] counts = new int[1];
                MPI.COMM_WORLD.Recv(counts, 0, 1, MPI.INT, src, 5000);
                for (int j = 0; j < counts[0]; j++) {
                    byte[] diffBytes = recvChunkedBytes(src, 2000 + j);
                    String fname = filenames[processStart + j + (src * (totalFrames / size))]; // estimate index offset per src
                    try (FileOutputStream fos = new FileOutputStream(new File(outputPath, fname))) {
                        fos.write(diffBytes);
                    }
                }
            }
            new VideoProcessing().makeVideo(outputPath, "output.mp4", fps);
            Logger.log("Distributed processing complete.", LogLevel.Status);
        }

        MPI.Finalize();
    }


    private String[] distributeFilenames(String path, int rank) throws MPIException {
        String[] filenames = null;
        int[] count = new int[1];

        if (rank == 0) {
            File[] files = new File(path).listFiles((d, name) -> name.endsWith(".png"));
            if (files == null || files.length == 0) {
                Logger.log("No frames found.", LogLevel.Error);
                count[0] = 0;
                MPI.COMM_WORLD.Bcast(count, 0, 1, MPI.INT, 0);
                return null;
            }
            Arrays.sort(files, Comparator.comparingInt(f -> Integer.parseInt(f.getName().replaceAll("\\D", ""))));
            filenames = Arrays.stream(files).map(File::getName).toArray(String[]::new);
            count[0] = filenames.length;
        }

        MPI.COMM_WORLD.Bcast(count, 0, 1, MPI.INT, 0);
        if (count[0] == 0) return null;

        if (rank != 0) filenames = new String[count[0]];
        MPI.COMM_WORLD.Bcast(filenames, 0, count[0], MPI.OBJECT, 0);
        return filenames;
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

    private void sendFrame(BufferedImage frame, int dest) throws IOException, MPIException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(frame, "PNG", baos);
            byte[] frameData = baos.toByteArray();
            sendChunkedBytes(frameData, dest, 1000);
        }
    }

    private BufferedImage receiveFrame(int source) throws MPIException {
        try {
            byte[] frameData = recvChunkedBytes(source, 1000);
            return ImageIO.read(new ByteArrayInputStream(frameData));
        } catch (IOException e) {
            Logger.log("Failed to receive frame from rank " + source, LogLevel.Error);
            return null;
        }
    }

    private void sendChunkedBytes(byte[] data, int dest, int tagBase) throws MPIException {
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
            requests[i] = MPI.COMM_WORLD.Isend(data, start, len, MPI.BYTE, dest, tagBase + 1 + i);
        }

        // Wait for all sends to complete
        mpi.Request.Waitall(requests);
    }

    private byte[] recvChunkedBytes(int source, int tagBase) throws MPIException {
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
            requests[i] = MPI.COMM_WORLD.Irecv(data, offset, len, MPI.BYTE, source, tagBase + 1 + i);
            offset += len;
        }

        //wait for all receives to complete
        mpi.Request.Waitall(requests);

        return data;
    }


    private BufferedImage computeDifference(BufferedImage img1, BufferedImage img2, int rank) {
        int width = img1.getWidth();
        int height = img1.getHeight();
        BufferedImage diffImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        int colorCount = 1;

        // Difference threshold, tweak if needed
        int threshold = 10;

        int[][] diffPixels = new int[width][height];

        // Mark pixels differing over threshold
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;
                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;

                int diff = Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                if (diff > threshold) {
                    diffPixels[x][y] = -1; // Mark as different (uncolored)
                } else {
                    diffPixels[x][y] = 0;  // Same pixel
                    int origRgb = img2.getRGB(x, y); 
                    Color c = new Color(origRgb, true);
                    int dimmedRgb = new Color(c.getRed() / 3, c.getGreen() / 3, c.getBlue() / 3, 255).getRGB();
                    diffImg.setRGB(x, y, dimmedRgb);
                }
            }
        }

        // Flood fill coloring of diff regions
        Random rand = new Random(rank * 1000 + System.currentTimeMillis());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (diffPixels[x][y] == -1) {
                    colorCount++;
                    int fillColor = new Color(rand.nextInt(200) + 55, rand.nextInt(200) + 55, rand.nextInt(200) + 55, 255).getRGB();
                    floodFill(diffPixels, diffImg, x, y, -1, colorCount, fillColor, width, height, dx, dy);
                }
            }
        }

        return diffImg;
    }

    private void floodFill(int[][] pixels, BufferedImage img, int x, int y, int targetColor, int replacementColor, int fillRgb, int width, int height, int[] dx, int[] dy) {
        Queue<Point> queue = new LinkedList<>();
        queue.add(new Point(x, y));
        pixels[x][y] = replacementColor;
        img.setRGB(x, y, fillRgb);

        while (!queue.isEmpty()) {
            Point p = queue.poll();
            for (int i = 0; i < 4; i++) {
                int nx = p.x + dx[i];
                int ny = p.y + dy[i];
                if (nx >= 0 && ny >= 0 && nx < width && ny < height && pixels[nx][ny] == targetColor) {
                    pixels[nx][ny] = replacementColor;
                    img.setRGB(nx, ny, fillRgb);
                    queue.add(new Point(nx, ny));
                }
            }
        }
    }
}
