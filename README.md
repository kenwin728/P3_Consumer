# How To Run The Program
1. **Run P3_Consumer**
   - Run `ConsumerApp.java`, then input the configuration variables: c (number of consumer worker threads), q (Max Queue Length), Listen Port (Port number for listening to uploads. E.g. 8080), Http Port (port number for the GUI web server. E.g. 8000), output folder (Folder to store all uploaded videos. If it does not exist, it will be created automatically), and gui folder (folder where the index.html and script.js are located).
2. **Access GUI**
   - Open a web browser on any machine that can reach the consumer's machine.
   - Find IP address of the consumer's machine
     ```cmd
     ipconfig
     ```
     (Look for `IPv4 Address` under active connection.)
   - Navigate to `http://<consumer's IP>:<HTTP port>` (E.g. `http://192.168.1.101:8000`).
   - Web browser should show the list of uploaded videos. Hovering over a video name should start a muted preview of the first 10 seconds of the video, and clicking should play the full video with sound.
   - Web browser refreshes to fetch the updated video list every 30 seconds. This is to avoid interruptions in the preview. You can manually reload the page to get the updated video list.
