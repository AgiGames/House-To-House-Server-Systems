#include <Arduino.h>
#include <SPI.h>
#include <SD.h>
#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

const char* ssid = "CloudServer";
const char* password = "12345678";

int CS_PIN = D8; // Chip select pin on the ESP8266 NodeMCU 1.0
File file; // Instance of the File class
File uploadFile;

unsigned long startTime, endTime;
const float voltage = 3.3;
const float current = 0.15; // 150mA assumed for ESP8266 WiFi operations

ESP8266WebServer server(80);

void handleRoot() {
  String html = "<html>\n";
  html += "<head>\n";
  html += "<style>\n";
  html += "body { font-family: Arial, sans-serif; background-color: #f4f4f9; color: #333; text-align: center; }\n";
  html += "h1 { color: #333; }\n";
  html += "a { text-decoration: none; color: #007bff; padding: 5px 10px; margin: 5px; }\n";
  html += "a:hover { background-color: #007bff; color: white; border-radius: 5px; }\n";
  html += "form { margin-top: 20px; }\n";
  html += "input[type='file'] { margin: 10px 0; }\n";
  html += "input[type='submit'] { padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 5px; cursor: pointer; }\n";
  html += "input[type='submit']:hover { background-color: #218838; }\n";
  html += "</style>\n";
  html += "</head>\n";
  html += "<body>\n<h1>ESP8266 SD Card File Manager</h1>\n";
  html += "<form action='/upload' method='post' enctype='multipart/form-data'>\n";
  html += "<input type='file' name='data'>\n";
  html += "<input type='submit' value='Upload'>\n";
  html += "</form>\n";
  html += "<a href='/list'>List Files</a>\n";
  html += "<button onclick=\"window.location.href='/copy_to_laptop'\">Copy Files to Laptop</button>\n";
  html += "</body>\n</html>";
  server.send(200, "text/html", html);
}

void handleFileUpload() {
  HTTPUpload& upload = server.upload();

  if (upload.status == UPLOAD_FILE_START) {
    startTime = millis(); // Start timing
    String path = "/" + upload.filename;
    Serial.print("Uploading file: ");
    Serial.println(path);
    uploadFile = SD.open(path, FILE_WRITE);
    if (!uploadFile) {
      Serial.println("Failed to open file for writing.");
      server.send(500, "text/plain", "Failed to open file for writing.");
      return;
    }
  } else if (upload.status == UPLOAD_FILE_WRITE) {
    if (uploadFile) {
      uploadFile.write(upload.buf, upload.currentSize);  // Write received data chunk to file
      // Serial.print("Written chunk: ");
      // Serial.println(upload.currentSize);
    }
  } else if (upload.status == UPLOAD_FILE_END) {
    if (uploadFile) {
      uploadFile.close();  // Close the file after the upload is complete
      Serial.println("File upload complete.");
      endTime = millis(); // End timing
      unsigned long duration = endTime - startTime;
      float powerConsumption = (voltage * current * duration) / 1000; // Power in mWh
      Serial.printf("File upload complete. Time taken: %lu ms, Power consumed: %.3f mWh\n", duration, powerConsumption);
      server.send(200, "text/plain", "File uploaded successfully.\nTime: " + String(duration) + " ms\nPower: " + String(powerConsumption) + " mWh");
      server.send(200, "text/plain", "File uploaded successfully.");
    }
  }
}

void handleListFiles() {
  String html = "<html>\n<body>\n<h1>SD Card Files</h1>\n<ul>\n";
  html += "<style>\n";
  html += "ul { list-style-type: none; padding: 0; }\n";
  html += "li { background-color: #f9f9f9; padding: 10px; margin: 5px 0; border-radius: 5px; display: flex; justify-content: space-between; align-items: center; }\n";
  html += "a { text-decoration: none; color: #007bff; padding: 5px 10px; margin: 0 5px; border-radius: 5px; }\n";
  html += "a:hover { background-color: #007bff; color: white; }\n";
  html += "</style>\n";
  
  File root = SD.open("/");

  if (!root) {
    server.send(500, "text/plain", "Failed to open SD card.");
    return;
  }

  if (!root.isDirectory()) {
    server.send(500, "text/plain", "Root is not a directory.");
    return;
  }

  File file = root.openNextFile();
  while (file) {
    html += "<li>";
    html += file.name();
    if (file.isDirectory()) {
      html += "/";
    } else {
      html += " (" + String(file.size()) + " bytes)";
      // Add Delete and Download buttons next to each file
      String fileName = file.name();
      html += " <a href='/delete?file=" + fileName + "'>Delete</a>";
      html += " <a href='/download?file=" + fileName + "'>Download</a>";
    }
    html += "</li>\n";
    file.close();
    file = root.openNextFile();
  }

  html += "</ul>\n<a href='/'>Go Back</a>\n</body>\n</html>";
  server.send(200, "text/html", html);
}

void handleDeleteFile() {
  String filename = server.arg("file");  // Get the file name from the query string

  if (SD.exists(filename.c_str())) {
    if (SD.remove(filename.c_str())) {
      Serial.print("File deleted: ");
      Serial.println(filename);
      server.send(200, "text/plain", "File deleted successfully.");
    } else {
      server.send(500, "text/plain", "Failed to delete file.");
    }
  } else {
    server.send(404, "text/plain", "File not found.");
  }
}

void handleDownloadFile() {
  String filename = server.arg("file");  // Get the file name from the query string

  if (SD.exists(filename.c_str())) {
    File downloadFile = SD.open(filename.c_str(), FILE_READ);

    // Set the appropriate content type for the file (based on its extension)
    String contentType = "application/octet-stream";  // Default to binary
    if (filename.endsWith(".txt")) {
      contentType = "text/plain";
    } else if (filename.endsWith(".html")) {
      contentType = "text/html";
    } else if (filename.endsWith(".css")) {
      contentType = "text/css";
    } else if (filename.endsWith(".js")) {
      contentType = "application/javascript";
    } else if (filename.endsWith(".png")) {
      contentType = "image/png";
    } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
      contentType = "image/jpeg";
    } else if (filename.endsWith(".pdf")) {
      contentType = "application/pdf";
    }

    startTime = millis(); // Start timing
    // Set the correct content-disposition header to indicate the file should be downloaded
    server.sendHeader("Content-Disposition", "attachment; filename=" + filename);
    server.streamFile(downloadFile, contentType);  // Stream the file as the determined content type
    downloadFile.close();
    endTime = millis(); // End timing
    unsigned long duration = endTime - startTime;
    float powerConsumption = (voltage * current * duration) / 1000; // Power in mWh
    Serial.printf("File download complete. Time taken: %lu ms, Power consumed: %.3f mWh\n", duration, powerConsumption);
  } else {
    server.send(404, "text/plain", "File not found.");
  }
}

int createFile(String filename)
{
  filename.trim();
  filename.replace(' ', '_');
  filename.remove(filename.indexOf('\r'), 1);  // Remove any carriage return
  filename.remove(filename.indexOf('\n'), 1);  // Remove any newline
  Serial.println(filename);
  if (SD.exists(filename.c_str())) {
    file = SD.open(filename.c_str(), FILE_WRITE);
    return 0;
  }

  file = SD.open(filename.c_str(), FILE_WRITE);
  if (file)
  {
    Serial.println("File created successfully.");
    return 1;
  }
  else
  {
    Serial.println("Error while creating file.");
    return 0;
  }
}

void handleCopyToLaptop() {
  Serial.println("TRANSFER_START");
  File root = SD.open("/");
  File file = root.openNextFile();
  while (file) {
    Serial.print("FILE_START:");
    Serial.println(file.name());
    while (file.available()) Serial.write(file.read());
    Serial.println("\nFILE_END");
    file.close();
    file = root.openNextFile();
  }
  Serial.println("TRANSFER_END");
  server.send(200, "text/plain", "File transfer started. Run the Python script on your laptop.");
}

void printDirectory(File dir, int numTabs) {
  while (true) {

    File entry = dir.openNextFile();
    if (!entry) {
      // no more files
      break;
    }
    for (uint8_t i = 0; i < numTabs; i++) { Serial.print('\t'); }
    Serial.print(entry.name());
    if (entry.isDirectory()) {
      Serial.println("/");
      printDirectory(entry, numTabs + 1);
    } else {
      // files have sizes, directories do not
      Serial.print("\t\t");
      Serial.print(entry.size(), DEC);
      time_t cr = entry.getCreationTime();
      time_t lw = entry.getLastWrite();
      struct tm* tmstruct = localtime(&cr);
      Serial.printf("\tCREATION: %d-%02d-%02d %02d:%02d:%02d", (tmstruct->tm_year) + 1900, (tmstruct->tm_mon) + 1, tmstruct->tm_mday, tmstruct->tm_hour, tmstruct->tm_min, tmstruct->tm_sec);
      tmstruct = localtime(&lw);
      Serial.printf("\tLAST WRITE: %d-%02d-%02d %02d:%02d:%02d\n", (tmstruct->tm_year) + 1900, (tmstruct->tm_mon) + 1, tmstruct->tm_mday, tmstruct->tm_hour, tmstruct->tm_min, tmstruct->tm_sec);
    }
    entry.close();
  }
}

void setup() {
  Serial.begin(9600);
  Serial.println("---------------------------");

  bool _SD_OK=0;
  pinMode(CS_PIN, OUTPUT);
  _SD_OK=SD.begin(CS_PIN);
  
  if(_SD_OK){
    Serial.println("SD card init done");
  }else{
    Serial.println("Check SD card Connection");
  }

  // Connect to Wi-Fi
  WiFi.softAP(ssid, password);
  Serial.println("Connecting to Wi-Fi");

  server.on("/", handleRoot);
  server.on("/upload", HTTP_POST, []() { server.send(200, "text/plain", "File Uploaded."); }, handleFileUpload);
  server.on("/list", HTTP_GET, handleListFiles);
  server.on("/delete", HTTP_GET, handleDeleteFile);  // Handle file deletion
  server.on("/download", HTTP_GET, handleDownloadFile);  // Handle file download
  server.on("/copy_to_laptop", HTTP_GET, handleCopyToLaptop);

  server.begin();
}

void loop() {
  server.handleClient();
}
