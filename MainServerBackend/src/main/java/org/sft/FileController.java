package org.sft;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

// accept API calls from any origin
@CrossOrigin(origins = "*")

// specifically stating the following class is a controller class for REST API so spring boot will detect it
@RestController

// the functions defined in this class will be called according to their mapping if /files exists in the URL
// eg: http://localhost:9090/files/{url_map_to_function}
@RequestMapping("/files")
public class FileController {

    // base directory from which files from different folders will be served or files are saved to different folders from uploads
    private static final String BASE_DIR = System.getProperty("user.dir") + "/uploads/";

    // function will be called if url has /files/upload
    // eg: http://localhost:9090/files/upload
    @PostMapping("/upload")
    // defining a function for uploading
    // param:
    // file -> MultiPartFile: files will be sent to backend in multiple chunks or parts
    // id -> String: id of the folder in which the files uploaded will be saved
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file, @RequestParam("id") String id) {
        try {
            // we don't want to save an empty file, so return a bad request error if file is empty
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Upload failed: File is empty.");
            }

            // make the directory with the folder id in which the files will be saved if the directory does not exist
            File userDir = new File(BASE_DIR + id);
            if (!userDir.exists() && !userDir.mkdirs()) {
                // if for some reason the directory could not be created, return an INTERNAL_SERVER_ERROR
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: Could not create directory.");
            }

            // save the file in the user directory (with folder id) and append the name with current time to not overwrite
            // any other files that might exist with same file name
            File savedFile = new File(userDir, System.currentTimeMillis() + "_" + file.getOriginalFilename());

            // transfer the bytes from uploaded file to saved file
            file.transferTo(savedFile);

            // return an ok response with "File uploaded successfully: {file_name}"
            return ResponseEntity.ok("File uploaded successfully: " + savedFile.getAbsolutePath());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
    }

    // function will be called if url has /files/list
    // eg: http://localhost:9090/files/list
    @GetMapping("/list")
    // define a function to return all file names and sizes in a folder with given id
    // param:
    // id -> String: id of the folder from which names and sizes of files will be returned
    public ResponseEntity<List<Map<String, Object>>> listFiles(@RequestParam("id") String id) {

        // open the directory
        File userDir = new File(BASE_DIR + id);

        // take references (kind of like paths) of the files in the folder
        File[] files = userDir.listFiles();

        /*
           we represent each file as a map as:
           {
            name: file_name
            size: file_size
           }

           to send all file names and sizes, we put each map that represents a single file in a list, so we will have
           a list of maps (representing files) to send as response
        */

        // list of maps
        List<Map<String, Object>> fileList = new ArrayList<>();

        // iterate through each file
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    Map<String, Object> fileInfo = new HashMap<>(); // make a new hashmap for representing the ith file
                    fileInfo.put("name", file.getName()); // set name to be the name of file
                    fileInfo.put("size", file.length()); // set size to size of file
                    fileList.add(fileInfo); // add the map to the list
                }
            }
        }
        return ResponseEntity.ok(fileList); // return the list of maps
    }


    // function will be called if url has /files/download
    // eg: http://localhost:9090/files/download
    @GetMapping("/download")
    // define a function to download a file, based on folder id and filename
    // param:
    // id -> String: id of the folder from which the file must be downloaded
    // filename -> String: name of the file to be downloaded
    public ResponseEntity<byte[]> downloadFile(@RequestParam("id") String id, @RequestParam("filename") String filename) {
        try {
            // open the file with base directory + folder id
            File file = new File(BASE_DIR + id + "/" + filename);
            if (!file.exists()) {
                // if the file does not exist, return a NOT_FOUND status
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(("File not found: " + filename).getBytes());
            }

            // take contents of file
            byte[] fileContent = Files.readAllBytes(file.toPath());

            // we have to make a new header, to tell the browser that we are streaming binary data (as octet) and also
            // the file must be sent as a download prompt
            HttpHeaders headers = new HttpHeaders();

            // tells the browser that we are streaming binary data
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            // tells the browser to make a download prompt for the file
            headers.setContentDispositionFormData("attachment", filename);

            // return the file content, along with the headers and an OK response
            return new ResponseEntity<>(fileContent, headers, HttpStatus.OK);
        } catch (IOException e) {
            // if some error comes along the way send INTERNAL_SERVER_ERROR
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(("Download failed: " + e.getMessage()).getBytes());
        }
    }

    // not used anywhere for now, documentation will be done when it is used
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

    // function will be called if url has /files/delete
    // eg: http://localhost:9090/files/delete
    @DeleteMapping("/delete")
    // define a function to delete a file, based on folder id and filename
    // param:
    // id -> String: id of the folder from which the file must be deleted
    // filename -> String: name of the file to be deleted
    public ResponseEntity<String> deleteFile(@RequestParam("id") String id, @RequestParam("filename") String filename) {
        // open the file with base directory + folder id
        File file = new File(BASE_DIR + id + "/" + filename);
        if (!file.exists()) {
            // if the file does not exist, return a NOT_FOUND status
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found: " + filename);
        }

        // delete the file
        if (file.delete()) {
            // if it is deleted successfully, send an OK response
            return ResponseEntity.ok("File deleted successfully: " + filename);
        } else {
            // else send INTERNAL_SERVER_ERROR
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete file: " + filename);
        }
    }

}