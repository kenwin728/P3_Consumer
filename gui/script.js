document.addEventListener('DOMContentLoaded', () => {
    const videoListDiv = document.getElementById('video-list');
    const previewPlayer = document.getElementById('video-preview-player');
    const mainPlayer = document.getElementById('video-main-player');
    const previewTitle = document.getElementById('preview-title');
    const mainPlayerTitle = document.getElementById('main-player-title');

    // Keep track of the time update event listener function
    let timeUpdateHandler = null;

    // Function to stop and reset the preview player
    function stopPreview() {
        if (timeUpdateHandler) {
            previewPlayer.removeEventListener('timeupdate', timeUpdateHandler);
            timeUpdateHandler = null;
        }
        previewPlayer.pause();
        previewPlayer.removeAttribute('src');
        previewPlayer.load();
        previewTitle.textContent = 'Hover over video name for preview';
        console.log("Preview stopped and reset.");
    }

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

                console.log('Successfully fetched video list:', videoFiles);

                videoFiles.forEach(filename => {
                    const item = document.createElement('div');
                    item.classList.add('video-item');
                    item.textContent = filename;
                    item.dataset.filename = filename; // Store filename

                    // --- Event Listeners ---

                    // Mouse Enter on list item: Start preview
                    item.addEventListener('mouseenter', () => {
                        // If there is already an active time update listener, remove it
                        if (timeUpdateHandler) {
                            previewPlayer.removeEventListener('timeupdate', timeUpdateHandler);
                        }

                        const file = item.dataset.filename;
                        console.log(`Hover Enter: Preview ${file}`);
                        previewTitle.textContent = `Previewing: ${file}`;

                        previewPlayer.src = `/videos/${encodeURIComponent(file)}`;
                        previewPlayer.load();
                        const playPromise = previewPlayer.play();

                        // Define the handler that checks playback time
                        timeUpdateHandler = () => {
                            if (previewPlayer.currentTime >= 10) {
                                previewPlayer.pause();
                                console.log(`Preview paused at 10 seconds for ${file}`);
                                // Optionally, reset the time so that the preview always starts at 0
                                previewPlayer.currentTime = 0;
                                previewPlayer.removeEventListener('timeupdate', timeUpdateHandler);
                                timeUpdateHandler = null;
                            }
                        };

                        // Attach the time update listener
                        previewPlayer.addEventListener('timeupdate', timeUpdateHandler);

                        if (playPromise !== undefined) {
                            playPromise.catch(error => {
                                console.warn(`Preview autoplay prevented for ${file}:`, error);
                            });
                        }
                    });

                    // Mouse Leave from list item: Stop preview
                    item.addEventListener('mouseleave', () => {
                        console.log(`Hover Leave: Stop preview for ${item.dataset.filename}`);
                        stopPreview();
                    });

                    // Click on list item: Play in main player
                    item.addEventListener('click', () => {
                        const file = item.dataset.filename;
                        console.log(`Click: Play ${file} in main player`);
                        mainPlayerTitle.textContent = `Now Playing: ${file}`;

                        // Stop preview if it happens to be the same video
                        if (previewPlayer.currentSrc.endsWith(encodeURIComponent(file))) {
                            stopPreview();
                        }

                        mainPlayer.src = `/videos/${encodeURIComponent(file)}`;
                        mainPlayer.load();
                        const playPromise = mainPlayer.play();

                        if (playPromise !== undefined) {
                            playPromise.catch(error => {
                                console.error(`Main player play error for ${file}:`, error);
                                mainPlayerTitle.textContent = `Could not play: ${file}`;
                            });
                        }
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
