document.addEventListener('DOMContentLoaded', () => {
    const videoListDiv = document.getElementById('video-list');
    const previewPlayer = document.getElementById('video-preview-player');
    const mainPlayer = document.getElementById('video-main-player');
    const previewTitle = document.getElementById('preview-title');
    const mainPlayerTitle = document.getElementById('main-player-title');

    // Function to stop and reset the preview player
    function stopPreview() {
        previewPlayer.pause();
        previewPlayer.removeAttribute('src'); // Remove the source
        previewPlayer.load(); // Reset the player state
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
                        const file = item.dataset.filename;
                        console.log(`Hover Enter: Preview ${file}`);
                        previewTitle.textContent = `Previewing: ${file}`;

                        previewPlayer.src = `/videos/${encodeURIComponent(file)}`;
                        previewPlayer.load(); // Important to load the new source
                        const playPromise = previewPlayer.play();

                        if (playPromise !== undefined) {
                            playPromise.catch(error => {
                                console.warn(`Preview autoplay prevented for ${file}:`, error);
                                // Optionally update title if play fails
                                // previewTitle.textContent = `Preview failed to start for ${file}`;
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
                        // (though mouseleave should handle most cases)
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
    // Consider if this is needed; reloading might interrupt playback.
    // setInterval(loadVideoList, 30000); // Refresh every 30 seconds
});