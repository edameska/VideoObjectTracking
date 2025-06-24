# Video Object Tracking Project

This project tracks motion in videos by analyzing differences between consecutive frames. It extracts frames using FFmpeg, detects pixel changes, and highlights moving areas with transparent colors. The processed frames are then reassembled into a new video.

Three approaches were implemented: sequential, parallel, and distributed. Parallel and distributed methods outperform sequential, with parallel speeding up processing on multi-core CPUs and distributed scaling across cores but adding communication overhead. The project shows the trade-off between simplicity and scalability in video processing.
## Requirements  
- Java 11+  
- FFmpeg installed and accessible (e.g., `sudo apt install ffmpeg`)
- Video input must be `.mp4` format
- [MPJ Express](https://mpj-express.org/) for distributed processing (set up `MPJ_HOME` and use `mpjrun.sh`)
  
## Setup and Usage
### MPJ Express Setup

1. Download MPJ Express from [mpj-express.org](https://mpj-express.org/)

2. Extract and set environment variables:  
   - `MPJ_HOME` to MPJ folder  
   - Add `$MPJ_HOME/bin` to your `PATH`
  
```bash
export MPJ_HOME=/path/to/mpj-express
export PATH=$MPJ_HOME/bin:$PATH
```

Refer to [MPJ Express documentation](https://mpj-express.org/docs/readme/README) for detailed instructions.

### Usage
1. Compile the utility classes first
2. Sequential version
  - Compile the sequential version
  - Run the sequential version with:

```bash
java sequential.Main <path_to_input_video.mp4>
```
3. Parallel version
  - Compile the parallel version
  - Run the parallel version with:

```bash
java parallel.Main <path_to_input_video.mp4>
```
4. Distributed Version
- Compile the distributed version:
```bash
javac -cp .:$MPJ_HOME/lib/mpj.jar distributed/*.java
```
- Run the distributed version, specifying the number of processes
```bash
$MPJ_HOME/bin/mpjrun.sh -np <number_of_processes> -cp . distributed.Main <path_to_video_file>
```


