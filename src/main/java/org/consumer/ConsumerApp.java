package org.consumer;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*; // Import Files, Paths, Path, InvalidPathException
import java.util.ArrayList;
import java.util.Collections;
import java.util.InputMismatchException; // Import for error handling
import java.util.List;
import java.util.Scanner; // Import Scanner
import java.util.concurrent.*;

public class ConsumerApp {

    public static void main(String[] args) {

        // Use Scanner for interactive input
        Scanner scanner = new Scanner(System.in);

        // --- Configuration Variables ---
        int c = 0;
        int q = 0;
        int listenPort = 0;
        int httpPort = 0;
        Path outputFolder = null;
        Path guiFolder = null;


        System.out.println("--- Consumer Configuration ---");

        // --- Get Number of Consumer Threads (c) ---
        while (c <= 0) {
            System.out.print("Enter number of consumer worker threads (positive integer, e.g., 4): ");
            try {
                c = scanner.nextInt();
                if (c <= 0) {
                    System.err.println("Error: Number of threads must be positive.");
                }
            } catch (InputMismatchException e) {
                System.err.println("Error: Invalid input. Please enter a whole number.");
            } finally {
                scanner.nextLine(); // Consume newline
            }
        }

        // --- Get Max Queue Length (q) ---
        while (q <= 0) {
            System.out.print("Enter max connection queue size (positive integer, e.g., 10): ");
            try {
                q = scanner.nextInt();
                if (q <= 0) {
                    System.err.println("Error: Queue size must be positive.");
                }
            } catch (InputMismatchException e) {
                System.err.println("Error: Invalid input. Please enter a whole number.");
            } finally {
                scanner.nextLine(); // Consume newline
            }
        }

        // --- Get Listen Port ---
        while (listenPort <= 0 || listenPort > 65535) {
            System.out.print("Enter port number for listening to uploads (1-65535, e.g., 8080): ");
            try {
                listenPort = scanner.nextInt();
                if (listenPort <= 0 || listenPort > 65535) {
                    System.err.println("Error: Port number must be between 1 and 65535.");
                }
            } catch (InputMismatchException e) {
                System.err.println("Error: Invalid input. Please enter a whole number.");
            } finally {
                scanner.nextLine(); // Consume newline
            }
        }

        // --- Get HTTP Port ---
        while (httpPort <= 0 || httpPort > 65535 || httpPort == listenPort) {
            System.out.print("Enter port number for the GUI web server (1-65535, different from listen port, e.g., 8000): ");
            try {
                httpPort = scanner.nextInt();
                if (httpPort <= 0 || httpPort > 65535) {
                    System.err.println("Error: Port number must be between 1 and 65535.");
                } else if (httpPort == listenPort) {
                    System.err.println("Error: HTTP port must be different from the upload listen port (" + listenPort + ").");
                }
            } catch (InputMismatchException e) {
                System.err.println("Error: Invalid input. Please enter a whole number.");
            } finally {
                scanner.nextLine(); // Consume newline
            }
        }

        // --- Get Output Folder ---
        while (outputFolder == null) {
            System.out.print("Enter path for saving uploaded videos (e.g., output_videos): ");
            String pathStr = scanner.nextLine().trim();
            if (pathStr.isEmpty()){
                System.err.println("Error: Output path cannot be empty.");
                continue;
            }
            try {
                outputFolder = Paths.get(pathStr);
                // We'll try creating it later, just validate the path string format now.
            } catch (InvalidPathException e) {
                System.err.println("Error: Invalid path format: " + pathStr);
                outputFolder = null; // Reset to loop again
            }
        }


        // --- Get GUI Folder ---
        while (guiFolder == null) {
            System.out.print("Enter path to the folder containing GUI files (index.html, script.js) (e.g., gui): ");
            String pathStr = scanner.nextLine().trim();
            if (pathStr.isEmpty()){
                System.err.println("Error: GUI path cannot be empty.");
                continue;
            }
            try {
                Path tempPath = Paths.get(pathStr);
                // Validate that it IS a directory right away
                if (Files.isDirectory(tempPath)) {
                    guiFolder = tempPath;
                } else {
                    System.err.println("Error: Path is not a valid directory or does not exist: " + tempPath.toAbsolutePath());
                }
            } catch (InvalidPathException e) {
                System.err.println("Error: Invalid path format: " + pathStr);
            }
        }


        // Close the scanner
        scanner.close();

        // --- Display Final Configuration ---
        System.out.println("\n--- Starting Consumer with Configuration ---");
        System.out.println("Worker Threads (c): " + c);
        System.out.println("Max Queue Size (q): " + q);
        System.out.println("Upload Listen Port: " + listenPort);
        System.out.println("GUI HTTP Port:      " + httpPort);
        System.out.println("Output Folder:      " + outputFolder.toAbsolutePath());
        System.out.println("GUI Folder:         " + guiFolder.toAbsolutePath());
        System.out.println("------------------------------------------");


        // --- Setup (Output folder creation - same as before) ---
        try {
            if (!Files.exists(outputFolder)) {
                Files.createDirectories(outputFolder);
                System.out.println("Created output directory: " + outputFolder);
            }
            // GUI folder existence already checked during input
        } catch (IOException e) {
            System.err.println("ERROR: Could not create output directory: " + outputFolder + " - " + e.getMessage());
            System.exit(1);
        }


        // --- The rest of the logic remains the same ---

        // Use ArrayBlockingQueue for fixed-size bounded queue
        BlockingQueue<Socket> connectionQueue = new ArrayBlockingQueue<>(q);
        // Use a synchronized list to store filenames for the GUI handler
        List<String> uploadedFiles = Collections.synchronizedList(new ArrayList<>());
        // Populate initial list from existing files in output dir
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputFolder)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    uploadedFiles.add(file.getFileName().toString());
                }
            }
            System.out.println("Found existing files in output folder: " + uploadedFiles);
        } catch (IOException e) {
            System.err.println("Warning: Could not list existing files in output directory: " + e.getMessage());
        }

        // --- Start Worker Threads ---
        ExecutorService workerPool = Executors.newFixedThreadPool(c);
        for (int i = 0; i < c; i++) {
            workerPool.submit(new FileHandlerTask(connectionQueue, outputFolder, uploadedFiles));
        }

        // --- Start HTTP Server for GUI ---
        HttpServer httpServer = null;
        try {
            httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
            httpServer.createContext("/", new GuiHttpHandler(guiFolder, outputFolder, uploadedFiles));
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
            System.out.println("HTTP server started on port " + httpPort);
        } catch (IOException e) {
            System.err.println("ERROR: Could not start HTTP server on port " + httpPort + ": " + e.getMessage());
            workerPool.shutdownNow();
            System.exit(1);
        }

        // --- Start Network Listener (Main Thread) ---
        // ... (Rest of the try/finally block for ServerSocket and shutdown is identical to before) ...
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(listenPort);
            System.out.println("Consumer listening on port " + listenPort + "...");

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept(); // Blocks until connection
                    boolean accepted = connectionQueue.offer(clientSocket);
                    if (accepted) {
                        System.out.println("Connection accepted from " + clientSocket.getRemoteSocketAddress() + ", added to queue (" + connectionQueue.size() + "/" + q + ")");
                    } else {
                        System.out.println("Connection rejected from " + clientSocket.getRemoteSocketAddress() + " - Queue full (" + connectionQueue.size() + "/" + q + ")");
                        try { clientSocket.close(); } catch (IOException e) { /* Ignore close error */ }
                    }
                } catch (IOException e) {
                    if (serverSocket.isClosed()){
                        System.out.println("Server socket closed, stopping listener loop.");
                        break;
                    }
                    System.err.println("Error accepting connection: " + e.getMessage());
                    try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        } catch (IOException e) {
            System.err.println("ERROR: Could not start server socket on port " + listenPort + ": " + e.getMessage());
        } finally {
            System.out.println("Shutting down consumer...");
            if (httpServer != null) {
                httpServer.stop(1);
                System.out.println("HTTP server stopped.");
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                try { serverSocket.close(); } catch (IOException e) { /* Ignore */ }
            }
            workerPool.shutdown();
            try {
                if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    workerPool.shutdownNow();
                    if (!workerPool.awaitTermination(30, TimeUnit.SECONDS))
                        System.err.println("Worker pool did not terminate");
                }
            } catch (InterruptedException ie) {
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Worker pool shut down.");
            System.out.println("Consumer finished.");
        }
    }
}