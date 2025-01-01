package sequential;

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


public class SequentialProcessor {
    public void processFramesS(String imgPath, String outputPath, int fps) throws IOException, InterruptedException {
        File inputDir = new File(imgPath);
        File outputDir = new File(outputPath);


        File[] frames = inputDir.listFiles(((dir, name) -> name.endsWith(".png")));
        if(frames==null||frames.length==0) {
            Logger.log("No frames found in the input directory", LogLevel.Error);
            return;
        }
        //sort frames numerically by filename
        Arrays.sort(frames, Comparator.comparingInt(f -> Integer.parseInt(f.getName().replaceAll("\\D+", ""))));

        //check if output directory exists and make sure its empty
        if (outputDir.exists()) {
            File[] outputFiles = outputDir.listFiles();
            if (outputFiles != null && outputFiles.length > 0) {
                for (File file : outputFiles) {
                    if (!file.delete()) {
                        Logger.log("Failed to delete file: " + file.getName(), LogLevel.Error);
                    }
                }
            }
        }

        BufferedImage prevFrame=null;

        for(File frameFile : frames){
            BufferedImage currentFrame= ImageIO.read(frameFile);
            Logger.log("Processing frame: "+frameFile.getName(), LogLevel.Debug);
            if(prevFrame!=null){
                BufferedImage diffr= computeDifference(prevFrame,currentFrame);
                saveProccessedFrame(diffr, outputPath, frameFile.getName());
            }
            prevFrame=currentFrame;
        }
        VideoProcessing vp=new VideoProcessing();
        vp.makeVideo(outputPath, "output.mp4", fps);
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
                stack.push(new Point(px, py + 1)); //down
                stack.push(new Point(px, py - 1)); //up
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

        // diff/n is the average difference per color channel
        // 255 is the maximum value for a color channel
        // we get a value between 0 and 1
       // double p = diff / n / 255.0;

        return (diff / (3.0 * 255.0)) * 100.0; // Normalize and scale to percentage
    }
}
