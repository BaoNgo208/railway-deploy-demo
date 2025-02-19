package com.example.java_intern.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;


@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    private static final String UPLOAD_DIR = "C:\\Users\\ADMIN\\Whisper";

    @PostMapping("/cut")
    public ResponseEntity<byte[]> cutVideo(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty!".getBytes());
        }

        // Đảm bảo thư mục output tồn tại
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) uploadDir.mkdirs();

        // Tạo file tạm
        String inputFileName = UPLOAD_DIR + "temp_" + UUID.randomUUID() + ".mp4";
        File inputFile = new File(inputFileName);
        file.transferTo(inputFile);

        // Tạo file output
        String outputFileName = UPLOAD_DIR + "cut_" + UUID.randomUUID() + ".mp4";
        File outputFile = new File(outputFileName);

        // Cắt video bằng FFmpegFrameGrabber & FFmpegFrameRecorder
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputFile);
             FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, grabber.getImageWidth(), grabber.getImageHeight())) {

            grabber.start();
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("mp4");
            recorder.setFrameRate(grabber.getFrameRate());

            // Bỏ qua 2s đầu
            grabber.setTimestamp(2000000);

            long endTimestamp = grabber.getTimestamp() + 10_000_000; // Cắt 10s
            while (grabber.getTimestamp() < endTimestamp) {
                recorder.record(grabber.grabFrame());
            }

            recorder.stop();
            grabber.stop();
        }

        // Xóa file input sau khi xử lý
        inputFile.delete();

        // Trả về file đã cắt
        byte[] videoBytes = FileUtils.readFileToByteArray(outputFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFile.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(videoBytes);
    }


    @PostMapping("/merge")
    public ResponseEntity<byte[]> mergeVideos(@RequestParam("files") List<MultipartFile> files) throws IOException, InterruptedException {
        if (files.isEmpty() || files.size() < 2) {
            return ResponseEntity.badRequest().body("Cần ít nhất 2 video để ghép!".getBytes());
        }

        // Đảm bảo thư mục tồn tại
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) uploadDir.mkdirs();

        File tempListFile = File.createTempFile("file_list", ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempListFile))) {
            for (MultipartFile file : files) {
                File tempFile = File.createTempFile("temp_", ".mp4");
                file.transferTo(tempFile);
                writer.write("file '" + tempFile.getAbsolutePath().replace("\\", "/") + "'\n");
            }
        }

        // Tạo file output trong thư mục cố định
        String outputFileName = "merged_" + UUID.randomUUID() + ".mp4";
        File outputFile = new File(UPLOAD_DIR, outputFileName);

        // Lệnh FFmpeg để ghép video
        String mergeCmd = String.format("ffmpeg -f concat -safe 0 -i \"%s\" -c copy \"%s\"",
                tempListFile.getAbsolutePath(), outputFile.getAbsolutePath());

        // Chạy lệnh FFmpeg
        ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", mergeCmd);
        processBuilder.redirectErrorStream(true);
        Process mergeProcess = processBuilder.start();

        // Đọc và ghi log nếu cần
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mergeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Log the FFmpeg output
            }
        }


        // Xóa file tạm
        tempListFile.delete();

        // Đọc file output và trả về
        byte[] videoBytes = FileUtils.readFileToByteArray(outputFile);

        // Trả về video đã ghép
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(videoBytes);
    }
}
