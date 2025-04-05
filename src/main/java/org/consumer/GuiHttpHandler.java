package org.consumer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.URI;
import java.net.URLConnection; // For MIME types
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;


public class GuiHttpHandler implements HttpHandler {

    private final Path guiBasePath; // Path to the 'gui' folder
    private final Path videoBasePath; // Path to the 'output_videos' folder
    private final List<String> uploadedFiles; // Shared, thread-safe list

    public GuiHttpHandler(Path guiBasePath, Path videoBasePath, List<String> uploadedFiles) {
        this.guiBasePath = guiBasePath;
        this.videoBasePath = videoBasePath;
        this.uploadedFiles = uploadedFiles;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();
        System.out.println("HTTP Request: " + requestMethod + " " + path);


        try {
            if ("GET".equalsIgnoreCase(requestMethod)) {
                if ("/".equals(path) || "/index.html".equals(path)) {
                    serveFile(exchange, guiBasePath.resolve("index.html"), "text/html");
                } else if ("/script.js".equals(path)) {
                    serveFile(exchange, guiBasePath.resolve("script.js"), "application/javascript");
                } else if ("/api/videos".equals(path)) {
                    serveVideoList(exchange);
                } else if (path.startsWith("/videos/")) {
                    serveVideoFile(exchange, path);
                } else {
                    sendResponse(exchange, 404, "Not Found", "text/plain");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain"); // Only GET is supported
            }
        } catch (Exception e) {
            System.err.println("Error handling HTTP request " + path + ": " + e.getMessage());
            // e.printStackTrace(); // For debugging
            // Avoid sending detailed errors to client in production
            sendResponse(exchange, 500, "Internal Server Error", "text/plain");
        }
    }


    private void serveVideoList(HttpExchange exchange) throws IOException {
        // Create JSON manually (or use a library like Jackson/Gson)
        String json;
        synchronized (uploadedFiles) { // Access the list safely
            json = uploadedFiles.stream()
                    .map(f -> "\"" + f.replace("\\", "\\\\").replace("\"", "\\\"") + "\"") // Basic JSON escaping
                    .collect(Collectors.joining(",", "[", "]"));
        }
        sendResponse(exchange, 200, json, "application/json");
    }

    private void serveVideoFile(HttpExchange exchange, String path) throws IOException {
        // Extract filename: /videos/my_video.mp4 -> my_video.mp4
        String requestedFilename = path.substring("/videos/".length());

        // **Security:** Basic check - prevent path traversal
        if (requestedFilename.contains("/") || requestedFilename.contains("\\") || requestedFilename.equals("..")) {
            sendResponse(exchange, 400, "Bad Request: Invalid filename", "text/plain");
            return;
        }

        Path videoFile = videoBasePath.resolve(requestedFilename);

        // Check if the file actually exists and is in our list (optional check)
        boolean exists;
        synchronized(uploadedFiles){
            exists = uploadedFiles.contains(requestedFilename) && Files.exists(videoFile) && Files.isRegularFile(videoFile);
        }

        if (exists) {
            // Try to guess content type
            String contentType = URLConnection.guessContentTypeFromName(videoFile.toString());
            if (contentType == null) {
                contentType = "application/octet-stream"; // Default binary type
            }
            serveFile(exchange, videoFile, contentType);
        } else {
            System.err.println("Video file not found or not listed: " + videoFile);
            sendResponse(exchange, 404, "Video Not Found", "text/plain");
        }
    }


    private void serveFile(HttpExchange exchange, Path filePath, String contentType) throws IOException {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            System.err.println("File not found for serving: " + filePath);
            sendResponse(exchange, 404, "Not Found", "text/plain");
            return;
        }

        File file = filePath.toFile();
        long fileSize = file.length();

        exchange.getResponseHeaders().set("Content-Type", contentType);
        // Support range requests for seeking (important for video)
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

        // Handle Range requests (simplified)
        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        long start = 0;
        long end = fileSize - 1;
        boolean isRangeRequest = false;

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
            try {
                start = Long.parseLong(ranges[0]);
                if (ranges.length > 1) {
                    end = Long.parseLong(ranges[1]);
                }
                // Basic validation
                if (start < 0 || start >= fileSize || end < start || end >= fileSize) {
                    throw new NumberFormatException("Invalid range");
                }
                isRangeRequest = true;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.err.println("Invalid Range header: " + rangeHeader);
                // Ignore range request if invalid, serve full file with 200 OK
                isRangeRequest = false;
                start = 0;
                end = fileSize -1;
            }
        }

        long contentLength = (end - start) + 1;

        if (isRangeRequest) {
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            exchange.sendResponseHeaders(206, contentLength); // Partial Content
        } else {
            exchange.sendResponseHeaders(200, contentLength); // OK
        }


        // Stream the file content (respecting range if applicable)
        try (OutputStream os = exchange.getResponseBody();
             FileInputStream fis = new FileInputStream(file)) {
            if (start > 0) {
                fis.skip(start); // Skip to the start byte
            }

            byte[] buffer = new byte[8192];
            int bytesRead;
            long bytesRemaining = contentLength;
            while (bytesRemaining > 0 && (bytesRead = fis.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining))) != -1) {
                os.write(buffer, 0, bytesRead);
                bytesRemaining -= bytesRead;
            }
        } // Streams are closed here
        System.out.println("Served: " + filePath + (isRangeRequest ? (" Range: " + start + "-" + end) : " Full file") );
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String responseBody, String contentType) throws IOException {
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}
