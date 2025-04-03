package org.sft;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/files")
public class FileController {

    private static final String BASE_DIR = System.getProperty("user.dir") + "/uploads/";

    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file, @RequestParam("id") String id) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Upload failed: File is empty.");
            }

            File userDir = new File(BASE_DIR + id);
            if (!userDir.exists() && !userDir.mkdirs()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: Could not create directory.");
            }

            File savedFile = new File(userDir, System.currentTimeMillis() + "_" + file.getOriginalFilename());
            file.transferTo(savedFile);

            return ResponseEntity.ok("File uploaded successfully: " + savedFile.getAbsolutePath());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listFiles(@RequestParam("id") String id) {
        File userDir = new File(BASE_DIR + id);
        File[] files = userDir.listFiles();
        List<Map<String, Object>> fileList = new ArrayList<>();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", file.getName());
                    fileInfo.put("size", file.length());
                    fileList.add(fileInfo);
                }
            }
        }
        return ResponseEntity.ok(fileList);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam("id") String id, @RequestParam("filename") String filename) {
        try {
            File file = new File(BASE_DIR + id + "/" + filename);
            if (!file.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(("File not found: " + filename).getBytes());
            }

            byte[] fileContent = Files.readAllBytes(file.toPath());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);

            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Download failed: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/downloadAll")
    public ResponseEntity<byte[]> downloadAllFiles(@RequestParam("id") String id) {
        File userDir = new File(BASE_DIR + id);
        if (!userDir.exists() || !userDir.isDirectory()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        File[] files = userDir.listFiles();
        if (files == null || files.length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (File file : files) {
                if (file.isFile()) {
                    byte[] fileContent = Files.readAllBytes(file.toPath());
                    outputStream.write((file.getName() + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.write((fileContent.length + "\n").getBytes(StandardCharsets.UTF_8));
                    outputStream.write(fileContent);
                }
            }
            byte[] responseBytes = outputStream.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename("all_files.bin").build());

            return ResponseEntity.ok().headers(headers).body(responseBytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteFile(@RequestParam("id") String id, @RequestParam("filename") String filename) {
        File file = new File(BASE_DIR + id + "/" + filename);
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found: " + filename);
        }

        if (file.delete()) {
            return ResponseEntity.ok("File deleted successfully: " + filename);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete file: " + filename);
        }
    }

}