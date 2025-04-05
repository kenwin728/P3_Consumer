package org.consumer;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List; // Import List
import java.util.concurrent.BlockingQueue;

public class FileHandlerTask implements Runnable {

    private final BlockingQueue<Socket> connectionQueue;
    private final Path outputFolderPath;
    private final List<String> uploadedFiles; // Thread-safe list

    public FileHandlerTask(BlockingQueue<Socket> connectionQueue, Path outputFolderPath, List<String> uploadedFiles) {
        this.connectionQueue = connectionQueue;
        this.outputFolderPath = outputFolderPath;
        this.uploadedFiles = uploadedFiles;
    }

    @Override
    public void run() {
        System.out.println("[" + Thread.currentThread().getName() + "] Worker started.");
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = connectionQueue.take(); // Blocks until a connection is available
                System.out.println("[" + Thread.currentThread().getName() + "] Processing connection from: " + clientSocket.getRemoteSocketAddress());
                handleConnection(clientSocket);
            }
        } catch (InterruptedException e) {
            System.out.println("[" + Thread.currentThread().getName() + "] Worker interrupted.");
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
        System.out.println("[" + Thread.currentThread().getName() + "] Worker stopped.");
    }

    private void handleConnection(Socket clientSocket) {
        // Try-with-resources for socket streams
        try (InputStream socketInputStream = clientSocket.getInputStream();
             DataInputStream dataIn = new DataInputStream(socketInputStream)) { // Wrap for easier primitive reading

            // 1. Read filename length
            int fileNameLength = dataIn.readInt();
            if (fileNameLength <= 0 || fileNameLength > 1024) { // Basic sanity check
                throw new IOException("Invalid filename length received: " + fileNameLength);
            }

            // 2. Read filename bytes
            byte[] fileNameBytes = new byte[fileNameLength];
            dataIn.readFully(fileNameBytes);
            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);

            // Sanitize filename (important for security!)
            fileName = fileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_"); // Replace unsafe chars
            if (fileName.isEmpty() || fileName.equals(".") || fileName.equals("..")) {
                throw new IOException("Invalid filename after sanitization.");
            }


            // 3. Read file size
            long fileSize = dataIn.readLong();
            if (fileSize < 0) {
                throw new IOException("Invalid file size received: " + fileSize);
            }

            Path outputFile = outputFolderPath.resolve(fileName);

            System.out.println("[" + Thread.currentThread().getName() + "] Receiving file: " + fileName + " (" + fileSize + " bytes) -> " + outputFile);


            // 4. Read file content and save
            long bytesReceived = 0;
            try (OutputStream fileOutputStream = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                while (bytesReceived < fileSize && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - bytesReceived))) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                }
            } // File stream closed here

            if (bytesReceived != fileSize) {
                System.err.println("[" + Thread.currentThread().getName() + "] File size mismatch for " + fileName + ". Expected " + fileSize + ", received " + bytesReceived + ". Deleting partial file.");
                try { Files.deleteIfExists(outputFile); } catch (IOException delEx) { /* Ignore delete error */ }
            } else {
                System.out.println("[" + Thread.currentThread().getName() + "] Successfully received and saved: " + fileName);
                // Add to the list *after* successful save
                synchronized(uploadedFiles) { // Ensure thread safety for add
                    if (!uploadedFiles.contains(fileName)) {
                        uploadedFiles.add(fileName);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("[" + Thread.currentThread().getName() + "] Error handling connection: " + e.getMessage());
            // e.printStackTrace(); // For debugging
        } finally {
            try {
                clientSocket.close(); // Ensure socket is closed
            } catch (IOException e) {
                // Ignore closing error
            }
        }
    }
}
