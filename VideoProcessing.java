import java.io.IOException;

public class VideoProcessing {

    public void extractFrames(String inputVideoPath, String outputFolder, int fps) throws IOException, InterruptedException {
        String command = String.format("ffmpeg -i %s -vf fps=%d %s/frame_%%04d.png",
                //vf= video filter => fps is a filter
                //%%04d frames will be numbered with a 4digit zero padded integer
                inputVideoPath, fps, outputFolder);
        executeFFmpegCommand(command);
    }
    public void makeVideo(String inputImgPath, String outputFolder, int fps) throws IOException, InterruptedException {
        String command = String.format("ffmpeg -framerate %d -i %s/frame_%%04d.png -c:v libx264 -pix_fmt yuv420p %s",
                // -c:v libx264 = codec to use to encode the video
                //-pix_fmt yuv420p pixel format for compatibility
                fps, inputImgPath, outputFolder);
        executeFFmpegCommand(command);
    }

    private Process executeFFmpegCommand(String command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        process.waitFor();
        return process;
    }
}
