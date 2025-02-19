package com.example.java_intern.controller;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    private static final String UPLOAD_DIR = "/app/uploads/"; // Đảm bảo đường dẫn phù hợp với Linux

    @PostMapping("/cut")
    public ResponseEntity<byte[]> cutVideo(@RequestParam("file") MultipartFile file) throws IOException, InterruptedException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty!".getBytes());
        }

        // Tạo file tạm để lưu dữ liệu đầu vào
        File tempInputFile = File.createTempFile("temp_", ".mp4");
        file.transferTo(tempInputFile);

        // Đảm bảo thư mục output tồn tại
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) uploadDir.mkdirs();

        // Tạo file output trong thư mục cố định
        String outputFileName = "cut_" + UUID.randomUUID() + ".mp4";
        File outputFile = new File(UPLOAD_DIR + outputFileName);

        // Lệnh FFmpeg để cắt video
        String cutCmd = String.format("ffmpeg -i '%s' -ss 2 -t 100 -c copy '%s'",
                tempInputFile.getAbsolutePath(), outputFile.getAbsolutePath());

        // Chạy lệnh FFmpeg trên Linux
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", cutCmd);
        processBuilder.redirectErrorStream(true);
        Process cutProcess = processBuilder.start();
        cutProcess.waitFor();

        // Xóa file tạm sau khi cắt xong
        tempInputFile.delete();

        // Đọc file output và trả về
        byte[] videoBytes = FileUtils.readFileToByteArray(outputFile);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFileName)
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

        // Tạo danh sách file tạm để lưu video đầu vào
        File tempListFile = File.createTempFile("file_list", ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempListFile))) {
            for (MultipartFile file : files) {
                File tempFile = File.createTempFile("temp_", ".mp4");
                file.transferTo(tempFile);
                writer.write("file '" + tempFile.getAbsolutePath() + "'\n");
            }
        }

        // Tạo file output trong thư mục cố định
        String outputFileName = "merged_" + UUID.randomUUID() + ".mp4";
        File outputFile = new File(UPLOAD_DIR, outputFileName);

        // Lệnh FFmpeg để ghép video
        String mergeCmd = String.format("ffmpeg -f concat -safe 0 -i '%s' -c copy '%s'",
                tempListFile.getAbsolutePath(), outputFile.getAbsolutePath());

        // Chạy lệnh FFmpeg trên Linux
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", mergeCmd);
        processBuilder.redirectErrorStream(true);
        Process mergeProcess = processBuilder.start();

        // Đọc log FFmpeg
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mergeProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info(line);
            }
        }

        mergeProcess.waitFor();
        tempListFile.delete();

        // Đọc file output và trả về
        byte[] videoBytes = FileUtils.readFileToByteArray(outputFile);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(videoBytes);
    }
}
