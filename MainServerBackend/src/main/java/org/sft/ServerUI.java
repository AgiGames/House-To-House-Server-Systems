package org.sft;

import com.fazecast.jSerialComm.SerialPort;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.*;

import java.net.HttpURLConnection;

@SpringBootApplication
public class ServerUI {

    private static JLabel statusLabel; // status label to show url of backend server
    private static JButton startButton;
    private static JButton copyButton; // button to copy url
    private static String tunnelUrl = ""; // the actual url of the backend server in memory

    private static JComboBox<String> comSelector; // selection box for choosing the com to receive files from
    private static JTextField folderIDInput; // input for what folder with folder ID the received files from serial will go to

    private static JTextField destinationFolderID; // folder ID to which downloaded files will go to
    private static JTextField sourceFolderID; // folder ID from which files can be viewed and downloaded
    private static JTextField url; // the url from which files will be viewed and downloaded
    private static JButton listFiles; // a button that lists all files in the given url


    private static JTextArea logArea; // log outputs as files are sent in serial communication
    private static final int BAUD_RATE = 921600; // baud rate of the serial communication

    // base directory where all files and folders
    private static final String ROOT_DIR = System.getProperty("user.dir") + "/uploads/";

    public static void main(String[] args) {

        // tells java to run the creation of ui in the event dispatch thread (the thread where all gui operations are done)
        SwingUtilities.invokeLater(ServerUI::createUI);

    }

    // function to create all UI for the application
    private static void createUI() {

        JFrame frame = new JFrame("ServerUI"); // title of the app that appears at the top of the window when the gui is opened
        frame.setSize(600, 400); // size of the window
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // telling the operating system to just exit the program when program is closed

        // we use 2 tabs, one for switching on the main server with platform url,
        // another for serial communication to transfer files from ESP to main server via serial communication
        JTabbedPane tabbedPane = new JTabbedPane();

        // tab 1: main server URL

        // panel to hold all things that have to do with the server
        // border layout to sort ui elements to north and south
        JPanel serverPanel = new JPanel(new BorderLayout());
        startButton = new JButton("Start Server"); // button saying "start server"
        copyButton = new JButton("Copy URL"); // button saying "copy url"

        Font boldFont = new Font("Arial", Font.BOLD, 14); // Or any font of your choice

        startButton.setBackground(Color.DARK_GRAY);
        startButton.setForeground(Color.LIGHT_GRAY);
        startButton.setFont(boldFont);

        copyButton.setBackground(Color.DARK_GRAY);
        copyButton.setForeground(Color.LIGHT_GRAY);
        copyButton.setFont(boldFont);

        copyButton.setEnabled(false); // is disabled at start
        // label saying click start server to begin
        statusLabel = new JLabel("Click 'Start Server' to begin.", SwingConstants.CENTER);
                                                                      // center the label

        // set the event to execute the start server function for the start button
        startButton.addActionListener(e -> startServer());

        // set the event to execute copy to clipboard function for the copy url button
        copyButton.addActionListener(e -> copyToClipboard(tunnelUrl));

        // make a separate panel for the button
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(Color.DARK_GRAY);

        // create a thick black LineBorder
        LineBorder thickBlackLine = new LineBorder(Color.BLACK, 3);

        // set the border to your panel
        buttonPanel.setBorder(thickBlackLine);

        buttonPanel.add(startButton, BorderLayout.CENTER); // add the start button
        buttonPanel.add(copyButton, BorderLayout.EAST); // add the copy button

        // add the button panel to the south
        serverPanel.add(buttonPanel, BorderLayout.CENTER);

        // add the status label to the north
        serverPanel.add(statusLabel, BorderLayout.NORTH);

        // tab 2: serial file receiver

        // panel to hold all things that have to do with the serial file receiver
        // border layout to sort ui elements to north and south
        JPanel serialPanel = new JPanel(new BorderLayout());

        logArea = new JTextArea(); // text area to log outputs as files are received
        logArea.setEditable(false); // set it to not be editable

        // we try and give the text area a console look
        logArea.setBackground(new Color(30, 30, 30)); // dark gray/black
        logArea.setForeground(Color.WHITE);          // white text
        logArea.setFont(new Font("Consolas", Font.BOLD, 14)); // bold monospace
        logArea.setCaretColor(Color.WHITE);          // white blinking cursor
        logArea.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY)); // optional border
        logArea.setLineWrap(true);                   // wrap long lines
        logArea.setWrapStyleWord(true);              // wrap by word

        JButton startSerialButton = new JButton("Start Receiving"); // button saying "start receiving"

        // the receiving must be done on a new thread, so we set the action listener to generate an event e
        // that runs the receive files function on a new thread
        startSerialButton.addActionListener(e -> new Thread(ServerUI::receiveFiles).start());

        // we now make a selector UI element that makes you select the required COM port
        // and also make a text field where the desired user id can be entered

        // get all ports that are available
        SerialPort[] availablePorts = SerialPort.getCommPorts();
        if (availablePorts.length == 0) {
            // if no ports are available, then log (to the text area) saying no ports are available
            System.out.println("No COM ports detected. Is your device connected?");
        }

        // we extract all port names
        String[] portNames = new String[availablePorts.length];
        for (int i = 0; i < availablePorts.length; i++) {
            portNames[i] = availablePorts[i].getSystemPortName();
        }

        // we make a separate panel to hold com selector and folder id input text field
        JPanel comAndFolderIDPanel = new JPanel(new BorderLayout());
        comAndFolderIDPanel.setBackground(Color.GRAY);

        // give the panel a border and title
        TitledBorder titleBorder2 = BorderFactory.createTitledBorder("Select COM Port and Enter Folder ID   ");
        titleBorder2.setTitleColor(Color.WHITE);
        comAndFolderIDPanel.setBorder(titleBorder2);
        // make the com selector with the available options being the array of strings port names
        comSelector = new JComboBox<>(portNames);

        // create the text field to input the folder id
        folderIDInput = new JTextField();

        // add both ui elements to the desired panel
        comAndFolderIDPanel.add(comSelector, BorderLayout.NORTH);
        comAndFolderIDPanel.add(folderIDInput, BorderLayout.CENTER);

        // add the newly made panel into the main panel
        serialPanel.add(comAndFolderIDPanel, BorderLayout.NORTH);

        // add the log area to the panel at north
        serialPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        // add the button to the south
        serialPanel.add(startSerialButton, BorderLayout.SOUTH);

        // tab 3: other server file viewer
        JPanel fileViewerPanel = new JPanel(new BorderLayout());

        // we make 1 main panel to hold some inputs required
        JPanel fileViewerSubPanel1 = new JPanel(new GridLayout(1, 4));

        // panel for input of destination folder ID
        JPanel fileViewerSubPanel2 = new JPanel(new BorderLayout());
        destinationFolderID = new JTextField("0");
        fileViewerSubPanel2.setBackground(Color.GRAY);
        fileViewerSubPanel2.add(destinationFolderID);

        TitledBorder titleBorder3 = BorderFactory.createTitledBorder("Enter destination Folder ID   ");
        titleBorder3.setTitleColor(Color.WHITE);
        fileViewerSubPanel2.setBorder(titleBorder3);

        // panel for input of source url
        JPanel fileViewerSubPanel3 = new JPanel(new BorderLayout());
        url = new JTextField();
        fileViewerSubPanel3.setBackground(Color.GRAY);
        fileViewerSubPanel3.add(url);

        TitledBorder titleBorder4 = BorderFactory.createTitledBorder("Enter source server URL   ");
        titleBorder4.setTitleColor(Color.WHITE);
        fileViewerSubPanel3.setBorder(titleBorder4);

        // panel for input of source folder ID
        JPanel fileViewerSubPanel4 = new JPanel(new BorderLayout());
        sourceFolderID = new JTextField();
        fileViewerSubPanel4.setBackground(Color.GRAY);
        fileViewerSubPanel4.add(sourceFolderID);

        TitledBorder titleBorder5 = BorderFactory.createTitledBorder("Enter source folder ID   ");
        titleBorder5.setTitleColor(Color.WHITE);
        fileViewerSubPanel4.setBorder(titleBorder5);

        // button that says list files
        listFiles = new JButton("List Files");

        // add everything into the panel that will hold input components
        fileViewerSubPanel1.add(fileViewerSubPanel2);
        fileViewerSubPanel1.add(fileViewerSubPanel3);
        fileViewerSubPanel1.add(fileViewerSubPanel4);
        fileViewerSubPanel1.add(listFiles);

        // we make a separate panel to show and list the files
        JPanel fileListPanel = new JPanel();
        fileListPanel.setLayout(new BoxLayout(fileListPanel, BoxLayout.Y_AXIS));
        fileListPanel.setBackground(Color.gray);

        // we make the list files panel scrollable
        JScrollPane scrollPane = new JScrollPane(fileListPanel);

        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        scrollPane.setPreferredSize(new Dimension(400, 300)); // Adjust as needed

        // after the button is clicked, it will list all files so add an action listener and call listFiles() function when clicked
        listFiles.addActionListener(e -> listFiles(fileListPanel));

        // add main panel for inputs and the scrollable pane
        fileViewerPanel.add(fileViewerSubPanel1, BorderLayout.NORTH);
        fileViewerPanel.add(scrollPane, BorderLayout.CENTER);

        // add all three panels: server panel, serial panel, file viewer panel, to the tabbed pane
        tabbedPane.addTab("Server", serverPanel);
        tabbedPane.addTab("Serial Receiver", serialPanel);
        tabbedPane.addTab("Other Server's File Viewer", fileViewerPanel);

        frame.add(tabbedPane); // add the tabbed pane to the main frame
        frame.setVisible(true); // make the frame visible

    }

    private static void startServer() {

        // disable the start button once started (disable in GUI thread)
        SwingUtilities.invokeLater(() -> startButton.setEnabled(false));

        // make a new thread to run the spring boot application
        // SpringApplication.run(ServerUI.class) tells spring boot which is the main class
        Thread serverThread = new Thread(() -> SpringApplication.run(ServerUI.class));

        serverThread.setDaemon(true); // this tells the spring boot server thread to terminate if the parent thread terminates
        serverThread.start(); // starts the thread

        startCloudflared(); // starts cloudflare to make cloudflare url and portforward the server

    }

    // function to generate a cloudflare url for http://localhost:9090 (local ip of spring boot server)
    private static void startCloudflared() {

        // kills any cloudflared instances that might exist to start freshly
        killCloudflared();

        // start cloudflare on a new thread
        Thread tunnelThread = new Thread(() -> {
            try {

                // cloudflare is usually installed in the user directory
                // mine is installed in C:/users/agile/
                String userHome = System.getProperty("user.home");

                // making the path to the exe file of cloudflared
                String cloudflaredPath = userHome + "/cloudflared.exe";

                // process builder is used to execute terminal commands from java
                // so we use the command ".\cloudflare tunnel --url http://localhost:9090"
                ProcessBuilder pb = new ProcessBuilder(cloudflaredPath, "tunnel", "--url", "http://localhost:9090");

                // redirects all errors to the output stream of the process
                pb.redirectErrorStream(true);

                // build and start the process
                Process process = pb.start();

                // we now attempt to read the output (input when you look from java's perspective) from the process and filter out the url
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line; // representing each line of input read

                    // we will use regex to filter out the url, the general structure of the url is given as input
                    Pattern pattern = Pattern.compile("(https://[a-zA-Z0-9.-]+\\.trycloudflare\\.com)");

                    // keep reading the lines as long as they are available
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line); // print out the line just for seeing in the console log
                        Matcher matcher = pattern.matcher(line); // match the line with the matter
                        if (matcher.find()) { // if matcher did find something
                            tunnelUrl = matcher.group(1); // this make sures we only get the url from the line
                                                          // eg: if the line is like CloudFlare URL: {url}
                                                          // matcher.group(1) will return only the {url}

                            // since we run the cloudflare starting process in a different thread (not the same as GUI thread)
                            // we tell the OS to "change the text of the gui to the matched URL in the GUI thread"
                            // and "enable the copy button of the GUI in the GUI thread"
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText("Cloudflared URL: " + tunnelUrl);
                                copyButton.setEnabled(true);
                            });
                        }
                    }
                }
            } catch (IOException e) { // just print the error if something goes wrong
                e.printStackTrace();
            }
        });

        // set the process builder thread to terminate if it's parent thread terminates
        tunnelThread.setDaemon(true);

        // start the thread
        tunnelThread.start();

    }

    // function to kill any cloudflared instances that may be running
    private static void killCloudflared() {
        try {
            // use the process builder to execute kill command in cmd
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "cloudflared.exe");
            pb.redirectErrorStream(true);
            Process process = pb.start(); // start the process
            // read the lines and print out just for convenience
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }
            // if error comes say failed to terminate cloud flared
        } catch (IOException e) {
            System.out.println("Failed to terminate Cloudflared. It may not be running.");
        }
    }

    // function to put given text into the clipboard, not much to explain here
    private static void copyToClipboard(String text) {
        if (!text.isEmpty()) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(null, "URL copied to clipboard!", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // function that appends to the logArea any given message
    private static void log(String message) {
        // append to log area text area in the GUI thread
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    // function that receives files via selected COM port serial
    private static void receiveFiles() {

        // get the selected port name
        String selectedPortName = (String) comSelector.getSelectedItem();

        // if no port name was somehow selected, log in the text area and exit the function
        if (selectedPortName == null) {
            log("Transfer aborted: No COM port selected.");
            return;
        }

        // we get the serial port based on its name
        SerialPort port = SerialPort.getCommPort(selectedPortName);
        port.setBaudRate(BAUD_RATE); // set its baud rate
        port.setNumDataBits(8); // set the size of each character transmitted to be 8 bits (compatible with esp8266)
        port.setNumStopBits(SerialPort.ONE_STOP_BIT); // set the number of stop bits (bits that represent that a character has been sent)
                                                      // to 1
        port.setParity(SerialPort.NO_PARITY); // no even parity or odd parity error checking, for improving performance

        // make the port time out after 5 seconds of no received message via the COM port
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 5000, 0);

        // if cant open the port, log it
        if (!port.openPort()) {
            log("Error: Unable to open port " + selectedPortName);
            return;
        }

        // get the folder id from the input text field
        String folderId = folderIDInput.getText();

        // if folder id is empty or contains only empty spaces
        if (folderId == null || folderId.trim().isEmpty()) {
            // log it and close port and exit the function
            log("Transfer aborted: No Folder ID provided.");
            port.closePort();
            return;
        }

        // get the folder directory with the folder id
        // and make the directory (only makes if the directory doesn't exist)
        String saveDir = ROOT_DIR + "/" + folderId.trim();
        new File(saveDir).mkdirs();

        // log some relevant info to the frontend
        log("Listening on " + selectedPortName);
        log("Files will be saved to folder: " + folderId);

        // we read the info line by line from the port
        // we don't use a buffered reader here, as it expects characters.
        // since we transfer files via serial, we send binary data, not characters
        try (InputStream input = port.getInputStream()) {
            BufferedOutputStream currentFile = null; // the file being written to
            String filename = null; // name of the file
            int bytesRemaining = 0; // how many bytes remain to complete reading to finish writing the file
            StringBuilder lineBuffer = new StringBuilder(); // stores characters until a line is received

            while (true) {
                // if we are in the middle of file transfer
                if (bytesRemaining > 0 && currentFile != null) {
                    // they buffer will either be size of 4096, or however many bytes remain if bytes remaining is less than 4096
                    byte[] buffer = new byte[Math.min(4096, bytesRemaining)];
                    // we read the input and store it into the buffer
                    // the number of bytes read will go into read variable
                    int read = input.read(buffer);

                    // if while reading we get
                    // if all bytes are read, while bytes remaining being more than 0, then something went wrong
                    // so break of out of the loop after closing the file
                    if (read == -1) {
                        log("Unexpected end of stream while file was being transferred!");
                        currentFile.close(); // clean the file up
                        break;
                    }

                    // write the buffer into the file, the number of bytes is given by read
                    currentFile.write(buffer, 0, read);

                    // subtract number of files read from bytes remaining
                    bytesRemaining -= read;

                    // if bytes are read, we safely close the file, log file received, and wait for the next file
                    if (bytesRemaining == 0) {
                        currentFile.close();
                        currentFile = null; // important
                        log("File received successfully.");
                    }
                } else { // if we are not in the middle of file transfer, then file metadata like "TRANSFER_START", "FILE_SIZE" are being sent
                    int ch = input.read(); // read character
                    if (ch == -1) { // if character marks end of transfer something went wrong so break the loop
                        log("Unexpected end of stream while file was being transferred!");
                        break;
                    }

                    // if character is a new line, we extract the line
                    if (ch == '\n') {
                        String line = lineBuffer.toString().trim();

                        // a line is received, so set the buffer length to 0
                        lineBuffer.setLength(0);

                        // self-explanatory
                        if (line.equals("TRANSFER_START")) {
                            log("Starting file transfer...");
                        } else if (line.startsWith("FILE_START:")) { // finally we get the file name
                            // get the substring from the read line which contains the file name
                            filename = line.substring("FILE_START:".length()).trim();
                            // log the file name
                            log("Receiving " + filename + "...");
                        } else if (line.startsWith("FILE_SIZE:")) { // we now get the number of bytes of file
                            // store it in bytes remaining after using to substring function to get only the number of bytes
                            bytesRemaining = Integer.parseInt(line.substring("FILE_SIZE:".length()).trim());
                            // make a new output stream to write data to the directory as a file
                            currentFile = new BufferedOutputStream(new FileOutputStream(saveDir + "/" + filename));
                        } else if (line.equals("TRANSFER_END")) { // self-explanatory
                            log("Transfer complete.");
                            break;
                        }
                        // FILE_END is ignored as it’s implicitly handled by size
                    } else {
                        // keep appending to the buffer
                        lineBuffer.append((char) ch);
                    }
                }
            }
        } catch (IOException e) { // if any error comes log that error
            log("Error: " + e.getMessage());
        } finally {
            port.closePort(); // close the port after everything is done
        }

    }

    // function to check if a given string is an actual URL
    public static boolean isValidURL(String url) {
        try {
            new URL(url).toURI(); // Additional URI check for edge cases
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void listFiles(JPanel fileListPanel) {

        // check if the entered inputs are valid

        String destinationFolderIDString = destinationFolderID.getText().trim();
        boolean destinationFolderIDIsValid = !destinationFolderIDString.isEmpty();

        String sourceFolderIDString = sourceFolderID.getText().trim();
        boolean sourceFolderIDIsValid = !sourceFolderIDString.isEmpty();

        String urlString = url.getText().trim();
        boolean urlIsValid = isValidURL(urlString);

        if (!destinationFolderIDIsValid || !urlIsValid || !sourceFolderIDIsValid) {
            JOptionPane.showMessageDialog(
                    null,
                    "Please enter a valid destination folder ID, source URL and source folder ID.",
                    "Invalid Input",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }


        // we continue with listing if entered inputs are valid
        try {
            // construct full request URL
            String fullURL = urlString + "/files/list?id=" + sourceFolderIDString;

            URL requestURL = new URL(fullURL);
            HttpURLConnection conn = (HttpURLConnection) requestURL.openConnection();
            conn.setRequestMethod("GET"); // we send a get request

            // check response code
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                JOptionPane.showMessageDialog(null, "Server returned: " + conn.getResponseCode(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // read response
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder responseBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }

            reader.close();
            conn.disconnect();

            // parse JSON response
            JSONArray fileList = new JSONArray(responseBuilder.toString());

            fileListPanel.removeAll();

            for (int i = 0; i < fileList.length(); i++) {
                JSONObject fileInfo = fileList.getJSONObject(i);
                String name = fileInfo.getString("name");
                long size = fileInfo.getLong("size");

                // create a black panel for each file
                JPanel filePanel = new JPanel();
                filePanel.setBackground(Color.darkGray);
                filePanel.setLayout(new BorderLayout(10, 0));
                filePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

                // file label with white text
                JLabel fileLabel = new JLabel("• " + name + " (" + size + " bytes)");
                fileLabel.setForeground(Color.WHITE);

                // download button
                JButton downloadButton = new JButton("Download");
                downloadButton.setBackground(Color.GREEN);

                // add an action listener that sends a download file request when clicked
                downloadButton.addActionListener(e -> {
                    try {
                        // make the download url
                        String downloadUrl = url.getText().trim() + "/files/download?id=" +
                                URLEncoder.encode(sourceFolderIDString, StandardCharsets.UTF_8) +
                                "&filename=" + URLEncoder.encode(name, StandardCharsets.UTF_8);

                        // send GET request and download file
                        HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
                        connection.setRequestMethod("GET");

                        // check response code
                        int responseCode = connection.getResponseCode();

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            InputStream inputStream = connection.getInputStream();
                            // make the directory to which file will be written based
                            File saveDir = new File(ROOT_DIR + "/" + destinationFolderIDString);
                            if (!saveDir.exists()) {
                                saveDir.mkdirs();
                            }

                            // get the file
                            File outFile = new File(saveDir, name);
                            // write to file from input stream
                            Files.copy(inputStream, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            inputStream.close();

                            JOptionPane.showMessageDialog(null, "Downloaded: " + name);
                        } else {
                            JOptionPane.showMessageDialog(null, "Failed to download: " + name, "Error", JOptionPane.ERROR_MESSAGE);
                        }

                        connection.disconnect(); // disconnect after download done
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(null, "Error downloading file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });

                // add label and button to panel
                filePanel.add(fileLabel, BorderLayout.CENTER);
                filePanel.add(downloadButton, BorderLayout.EAST);

                // add the custom file panel to the main list panel
                fileListPanel.add(filePanel);
            }

            fileListPanel.revalidate(); // update UI
            fileListPanel.repaint();

        } catch (Exception e) { // if error show error
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error fetching file list: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

}
