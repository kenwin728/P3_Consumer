document.addEventListener('DOMContentLoaded', () => {
    const videoListDiv = document.getElementById('video-list');
    const videoPlayer = document.getElementById('video-player');
    const playerTitle = document.getElementById('player-title');
    const previewInfo = document.getElementById('preview-info');
    let previewTimeout = null; // To manage the 10s preview pause

    function loadVideoList() {
        fetch('/api/videos')
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                return response.json();
            })
            .then(videoFiles => {
                videoListDiv.innerHTML = ''; // Clear previous list
                if (videoFiles.length === 0) {
                    videoListDiv.textContent = 'No videos uploaded yet.';
                    return;
                }
                videoFiles.forEach(filename => {
                    const item = document.createElement('div');
                    item.classList.add('video-item');
                    item.textContent = filename;
                    item.dataset.filename = filename; // Store filename

                    // --- Event Listeners ---

                    // Click: Play the full video
                    item.addEventListener('click', () => {
                        const file = item.dataset.filename;
                        console.log(`Click: Play ${file}`);
                        playerTitle.textContent = `Now Playing: ${file}`;
                        videoPlayer.src = `/videos/${encodeURIComponent(file)}`; // URL encode filename
                        videoPlayer.muted = false; // Unmute for full playback
                        videoPlayer.load(); // Load the new source
                        videoPlayer.play().catch(e => console.error("Play error:", e)); // Attempt to play
                        clearTimeout(previewTimeout); // Cancel any pending preview pause
                         previewInfo.textContent = ' '; // Clear preview text
                    });

                    // Mouse Enter: Start preview (load & play muted for ~10s)
                    item.addEventListener('mouseenter', () => {
                        const file = item.dataset.filename;
                        console.log(`Hover: Preview ${file}`);
                        previewInfo.textContent = `Previewing: ${file}`;

                        // Use the same player for preview, but keep it muted
                        videoPlayer.src = `/videos/${encodeURIComponent(file)}`;
                        videoPlayer.muted = true;
                        videoPlayer.currentTime = 0; // Start from beginning
                        videoPlayer.load();
                        const playPromise = videoPlayer.play();

                        if (playPromise !== undefined) {
                            playPromise.then(_ => {
                                // Playback started
                                clearTimeout(previewTimeout); // Clear any existing timeout
                                previewTimeout = setTimeout(() => {
                                    if (videoPlayer.src.endsWith(encodeURIComponent(file)) && !videoPlayer.paused) {
                                        console.log(`Preview pause: ${file}`);
                                        videoPlayer.pause();
                                         previewInfo.textContent = 'Preview paused.';
                                    }
                                }, 10000); // Pause after 10 seconds
                            }).catch(error => {
                                // Autoplay was prevented.
                                console.warn("Preview autoplay prevented:", error);
                                previewInfo.textContent = 'Preview failed to start automatically.';
                                clearTimeout(previewTimeout);
                            });
                        }
                    });

                     // Mouse Leave: Stop preview / clear info (optional)
                      item.addEventListener('mouseleave', () => {
                          // Option 1: Pause immediately if it was the preview source
                          // if (videoPlayer.src.endsWith(encodeURIComponent(item.dataset.filename)) && !videoPlayer.paused) {
                          //    videoPlayer.pause();
                          // }
                          // Option 2: Just clear the timeout and info text
                          clearTimeout(previewTimeout);
                          previewInfo.textContent = 'Hover over a video name for a preview.';
                     });


                    videoListDiv.appendChild(item);
                });
            })
            .catch(error => {
                console.error('Error fetching video list:', error);
                videoListDiv.innerHTML = 'Error loading video list. Is the consumer running?';
            });
    }

    // Initial load
    loadVideoList();

    // Optional: Periodically refresh the list
    setInterval(loadVideoList, 30000); // Refresh every 30 seconds
});