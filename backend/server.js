const express = require('express');
const http = require('http');
const WebSocket = require('ws');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ noServer: true });

// Read the secret key from environment variables.
// For production, you should set this variable in your hosting environment.
// Example: export PLUGIN_SECRET="your-super-secret-key"
const PLUGIN_SECRET = process.env.PLUGIN_SECRET || 'change-me-to-a-secure-secret';

// In-memory store for the latest analytics data.
// For a more robust solution, you could use a database like Redis.
let latestData = null;

// A set of all connected web clients (browsers).
const webClients = new Set();
let pluginSocket = null;

wss.on('connection', (ws, req) => {
    const secretKey = req.headers['x-secret-key'];

    // --- Handle Plugin Connection ---
    if (secretKey === PLUGIN_SECRET) {
        if (pluginSocket) {
            console.log('A new plugin tried to connect, but one is already active. Closing the new connection.');
            ws.close();
            return;
        }

        pluginSocket = ws;
        console.log('ScalaAnalytics Plugin connected successfully.');

        ws.on('message', (message) => {
            try {
                // When we get data from the plugin, we parse it...
                const data = JSON.parse(message);
                console.log('Received data from plugin:', JSON.stringify(data, null, 2));

                // ...store it as the latest data...
                latestData = data;

                // ...and broadcast it to all connected web clients.
                broadcastToWebClients(JSON.stringify(latestData));
            } catch (error) {
                console.error('Failed to parse message from plugin:', error);
            }
        });

        ws.on('close', () => {
            console.log('ScalaAnalytics Plugin disconnected.');
            pluginSocket = null;
            // Optional: You might want to clear the data or set servers to an "offline" state.
            // For simplicity, we'll just keep the last known state.
        });

        ws.on('error', (error) => {
            console.error('Error on plugin socket:', error);
        });

    // --- Handle Web Client Connection ---
    } else {
        console.log('A web client connected.');
        webClients.add(ws);

        // If we already have data, send it to the newly connected client immediately.
        if (latestData) {
            ws.send(JSON.stringify(latestData));
        }

        ws.on('close', () => {
            console.log('A web client disconnected.');
            webClients.delete(ws);
        });

        ws.on('error', (error) => {
            console.error('Error on web client socket:', error);
        });
    }
});

// Function to send data to all connected web clients
function broadcastToWebClients(data) {
    console.log(`Broadcasting data to ${webClients.size} web client(s).`);
    webClients.forEach(client => {
        if (client.readyState === WebSocket.OPEN) {
            client.send(data);
        }
    });
}

// --- HTTP Server Setup ---
// This handles the WebSocket upgrade request.
server.on('upgrade', (request, socket, head) => {
    // We define a path for our WebSocket, e.g., /ws
    const pathname = new URL(request.url, `http://${request.headers.host}`).pathname;

    if (pathname === '/ws') {
        wss.handleUpgrade(request, socket, head, (ws) => {
            wss.emit('connection', ws, request);
        });
    } else {
        // If the path doesn't match, destroy the socket.
        socket.destroy();
    }
});

// A simple health check endpoint
app.get('/', (req, res) => {
    res.send('ScalaAnalytics Backend is running.');
});

const PORT = process.env.PORT || 8080;
server.listen(PORT, () => {
    console.log(`Server is listening on port ${PORT}`);
    console.log('Make sure your Velocity plugin config points to this address.');
    console.log(`Required secret key is: "${PLUGIN_SECRET}"`);
});
