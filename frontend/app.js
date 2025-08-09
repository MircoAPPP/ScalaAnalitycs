document.addEventListener('DOMContentLoaded', () => {
    // --- CONFIGURATION ---
    // Change this to your backend's WebSocket URL.
    // For local testing, it's likely 'ws://localhost:8080/ws'.
    // For production (e.g., on Netlify with a backend on Render/Heroku), it will be 'wss://your-backend-domain.com/ws'.
    const WEBSOCKET_URL = `ws://${window.location.hostname}:8080/ws`;

    // --- DOM ELEMENTS ---
    const statusEl = document.getElementById('connection-status');
    const totalPlayersEl = document.getElementById('total-players');
    const proxyCpuEl = document.getElementById('proxy-cpu');
    const proxyMemoryEl = document.getElementById('proxy-memory');
    const serverListEl = document.getElementById('server-list');

    let socket;
    let playerHistoryChart;

    // --- CHART.JS INITIALIZATION ---
    function initializeChart() {
        const ctx = document.getElementById('player-history-chart').getContext('2d');
        playerHistoryChart = new Chart(ctx, {
            type: 'line',
            data: {
                datasets: [{
                    label: 'Giocatori Totali',
                    data: [], // Data will be added dynamically
                    borderColor: 'rgba(0, 255, 0, 1)',
                    backgroundColor: 'rgba(0, 255, 0, 0.2)',
                    borderWidth: 2,
                    tension: 0.3,
                    fill: true,
                }]
            },
            options: {
                scales: {
                    x: {
                        type: 'time',
                        time: {
                            unit: 'minute',
                            tooltipFormat: 'PPpp',
                            displayFormats: {
                                minute: 'HH:mm'
                            }
                        },
                        ticks: { color: 'rgba(224, 224, 224, 0.8)' },
                        grid: { color: 'rgba(224, 224, 224, 0.1)' }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: { color: 'rgba(224, 224, 224, 0.8)', stepSize: 1 },
                        grid: { color: 'rgba(224, 224, 224, 0.1)' }
                    }
                },
                plugins: {
                    legend: {
                        labels: {
                            color: 'rgba(224, 224, 224, 0.8)'
                        }
                    }
                }
            }
        });
    }

    // --- WEBSOCKET HANDLING ---
    function connect() {
        console.log(`Attempting to connect to ${WEBSOCKET_URL}`);
        socket = new WebSocket(WEBSOCKET_URL);

        socket.onopen = () => {
            console.log('WebSocket connection established.');
            statusEl.textContent = 'CONNECTED';
            statusEl.className = 'status-connected';
        };

        socket.onmessage = (event) => {
            const data = JSON.parse(event.data);
            console.log('Received data:', data);
            updateDashboard(data);
        };

        socket.onclose = () => {
            console.log('WebSocket connection closed. Reconnecting in 3 seconds...');
            statusEl.textContent = 'DISCONNECTED';
            statusEl.className = 'status-disconnected';
            setTimeout(connect, 3000); // Attempt to reconnect after 3 seconds
        };

        socket.onerror = (error) => {
            console.error('WebSocket error:', error);
            socket.close(); // This will trigger the onclose event and reconnection logic
        };
    }

    // --- DASHBOARD UPDATES ---
    function updateDashboard(data) {
        // Update main stats
        totalPlayersEl.textContent = data.totalPlayers;
        proxyCpuEl.textContent = `${data.proxyInfo.cpuUsage.toFixed(2)}%`;
        proxyMemoryEl.textContent = `${data.proxyInfo.memoryUsed} MB`;

        // Update player history chart
        const now = data.timestamp;
        playerHistoryChart.data.datasets[0].data.push({ x: now, y: data.totalPlayers });

        // Optional: Limit the number of data points to avoid performance issues
        if (playerHistoryChart.data.datasets[0].data.length > 300) { // Keep last 5 minutes of data (300 points / 1 point per second)
            playerHistoryChart.data.datasets[0].data.shift();
        }
        playerHistoryChart.update();

        // Update server list
        updateServerList(data.servers);
    }

    function updateServerList(servers) {
        serverListEl.innerHTML = '<h2>Server Status</h2>'; // Clear and add title

        Object.values(servers).sort((a,b) => a.name.localeCompare(b.name)).forEach(server => {
            const playersHtml = server.players.length > 0
                ? `<ul>${server.players.map(p => `<li>${p}</li>`).join('')}</ul>`
                : '<p>Nessun giocatore online.</p>';

            const serverCardHtml = `
                <div class="server-card">
                    <div class="server-card-header">
                        <h3>${server.name}</h3>
                        <span class="player-count">${server.playerCount}</span>
                    </div>
                    <div class="player-list">
                        ${playersHtml}
                    </div>
                </div>
            `;
            serverListEl.innerHTML += serverCardHtml;
        });
    }

    // --- INITIALIZATION ---
    initializeChart();
    connect();
});
