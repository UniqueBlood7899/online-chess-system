package com.chess.network;

import java.io.*;
import java.net.*;
import com.chess.root.moves.Move;
import com.chess.model.Setting;
import com.chess.root.Game;
import com.chess.root.Board;
import com.chess.root.Field;
import com.chess.root.pieces.Piece;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkManager {
    private Socket socket;
    private ServerSocket serverSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isHost;
    private static final int PORT = 4444;
    private boolean connected = false;
    private Game game;
    private static final Logger LOG = Logger.getLogger(NetworkManager.class.getName());
    private Thread keepAliveThread;
    
    public boolean hostGame() {
        try {
            LOG.log(Level.INFO, "Starting server on port {0}", PORT);
            serverSocket = new ServerSocket(PORT);
            return true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to create server: {0}", e.getMessage());
            return false;
        }
    }
    
    public boolean waitForConnection() {
        try {
            LOG.log(Level.INFO, "Waiting for opponent to connect...");
            socket = serverSocket.accept();
            setupStreams();
            isHost = true;
            connected = true;
            startKeepAlive();
            LOG.log(Level.INFO, "Opponent connected from {0}", socket.getInetAddress().getHostAddress());
            return true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Connection failed: {0}", e.getMessage());
            return false;
        }
    }
    
    public boolean joinGame(String hostAddress) {
        try {
            LOG.log(Level.INFO, "Connecting to host at {0}", hostAddress);
            socket = new Socket(hostAddress, PORT);
            setupStreams();
            isHost = false;
            connected = true;
            startKeepAlive();
            LOG.log(Level.INFO, "Connected to host successfully");
            return true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to connect to host: {0}", e.getMessage());
            return false;
        }
    }
    
    private void setupStreams() throws IOException {
        try {
            // Close existing streams if they exist
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error closing output stream: {0}", e.getMessage());
                }
            }
            
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Error closing input stream: {0}", e.getMessage());
                }
            }
            
            // Set socket parameters
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0); // Infinite timeout
            
            // Create new streams
            synchronized (socket) {
                out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                out.flush();
                in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
            }
            LOG.log(Level.INFO, "Network streams established");
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to setup streams: {0}", e.getMessage());
            // Nullify streams on error to prevent using corrupted streams
            out = null;
            in = null;
            throw e;
        }
    }
    
    public void sendMove(Move move) {
        try {
            // Create a simplified move description to send
            MoveData moveData = new MoveData(
                move.getStartField().getColumn(),
                move.getStartField().getRow(),
                move.getField().getColumn(),
                move.getField().getRow(),
                move.getClass().getSimpleName()
            );
            
            LOG.log(Level.INFO, "Sending move: {0}", move.getNotation());
            
            // Ensure the connection is stable before sending
            if (socket != null && socket.isConnected() && !socket.isClosed()) {
                synchronized (out) {
                    try {
                        // Check output stream
                        if (out == null) {
                            setupStreams();
                        }
                        
                        out.writeObject(moveData);
                        out.flush();
                        out.reset(); // This is important to prevent caching of objects
                        
                        LOG.log(Level.INFO, "Move sent successfully");
                    } catch (IOException e) {
                        LOG.log(Level.SEVERE, "Failed while writing to stream: {0}", e.getMessage());
                        handleDisconnect();
                    }
                }
            } else {
                LOG.log(Level.SEVERE, "Cannot send move: socket is not connected");
                handleDisconnect();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to send move: {0}", e.getMessage());
            handleDisconnect();
        }
    }
    
    public Move receiveMove() throws IOException {
        if (!connected || socket == null || socket.isClosed() || in == null) {
            throw new IOException("Not connected");
        }

        try {
            synchronized (in) {
                LOG.log(Level.INFO, "Waiting for move...");
                Object received = in.readObject();
                
                // Handle keep-alive messages
                if (received instanceof String) {
                    String message = (String) received;
                    if ("PING".equals(message)) {
                        LOG.log(Level.FINE, "Received PING, sending PONG");
                        synchronized (out) {
                            out.writeObject("PONG");
                            out.flush();
                            out.reset();
                        }
                        // Recursively try to receive the actual move
                        return receiveMove();
                    } else if ("PONG".equals(message)) {
                        LOG.log(Level.FINE, "Received PONG response, continuing to wait for move");
                        // This is a PONG response to our PING, keep waiting for the actual move
                        return receiveMove();
                    } else {
                        LOG.log(Level.WARNING, "Received unexpected string message: {0}", message);
                        // For any other string message, keep waiting for the actual move
                        return receiveMove();
                    }
                }
                
                if (received instanceof MoveData) {
                    MoveData moveData = (MoveData) received;
                    LOG.log(Level.INFO, "Received move data: {0},{1} -> {2},{3}", 
                        new Object[]{moveData.startCol, moveData.startRow, moveData.targetCol, moveData.targetRow});
                    
                    // Find the fields on the board
                    Board board = game.getBoard();
                    Field startField = board.getField(moveData.startCol, moveData.startRow);
                    Field targetField = board.getField(moveData.targetCol, moveData.targetRow);
                    
                    if (startField == null || targetField == null) {
                        LOG.log(Level.SEVERE, "Invalid field coordinates received");
                        return null;
                    }
                    
                    // Get pieces
                    Piece piece = startField.getPiece();
                    Piece victim = targetField.getPiece();
                    
                    // If piece is null (already moved), find a piece of that type that can move to the target
                    if (piece == null) {
                        LOG.log(Level.WARNING, "Piece not found at starting position, trying to find matching piece");
                        // Determine color of piece to search for - host is white, client is black
                        boolean pieceColor = isHost; // If host is receiving, then looking for black piece
                        
                        // Search all pieces of the right color
                        for (Piece p : board.getPieces(pieceColor)) {
                            if (p.canMoveTo(targetField)) {
                                piece = p;
                                LOG.log(Level.INFO, "Found alternative piece that can make the move");
                                break;
                            }
                        }
                        
                        if (piece == null) {
                            LOG.log(Level.SEVERE, "No piece found that can make the move");
                            return null;
                        }
                    }
                    
                    // Create the move based on the data
                    String moveType = moveData.moveType;
                    if (moveType == null || moveType.isEmpty()) {
                        moveType = "Move"; // Default to regular move
                    }
                    
                    try {
                        Move move = board.createMove(moveType, piece, targetField, victim);
                        if (move == null) {
                            LOG.log(Level.SEVERE, "Could not create move of type: {0}", moveType);
                            return null;
                        }
                        LOG.log(Level.INFO, "Move processed successfully");
                        return move;
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Error creating move: {0}", e.getMessage());
                        return null;
                    }
                } else {
                    LOG.log(Level.WARNING, "Received unexpected object type: {0}", 
                        received != null ? received.getClass().getName() : "null");
                    return null;
                }
            }
        } catch (ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Failed to deserialize move: {0}", e.getMessage());
            return null;
        } catch (IOException e) {
            if (connected) {
                LOG.log(Level.SEVERE, "Failed to receive move: {0}", e.getMessage());
                handleDisconnect();
            }
            throw e;
        }
    }
    
    public void sendGameSettings(Setting settings) {
        try {
            LOG.log(Level.INFO, "Sending game settings");
            out.writeObject(settings);
            out.flush();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to send settings: {0}", e.getMessage());
            handleDisconnect();
        }
    }
    
    public Setting receiveGameSettings() {
        try {
            LOG.log(Level.INFO, "Waiting for game settings...");
            Setting settings = (Setting) in.readObject();
            LOG.log(Level.INFO, "Game settings received");
            return settings;
        } catch (IOException | ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Failed to receive settings: {0}", e.getMessage());
            handleDisconnect();
            return null;
        }
    }
    
    private void handleDisconnect() {
        connected = false;
        if (game != null) {
            game.handleDisconnection();
        }
    }
    
    public void setGame(Game game) {
        this.game = game;
    }
    
    public boolean isHost() {
        return isHost;
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public void close() throws IOException {
        connected = false;
        
        // Stop keep-alive thread
        stopKeepAlive();
        
        // Close streams first
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing output stream: {0}", e.getMessage());
            }
            out = null;
        }
        
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing input stream: {0}", e.getMessage());
            }
            in = null;
        }
        
        // Close socket next
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing socket: {0}", e.getMessage());
            }
            socket = null;
        }
        
        // Close server socket last
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Error closing server socket: {0}", e.getMessage());
            }
            serverSocket = null;
        }
        
        LOG.log(Level.INFO, "Network connection closed");
    }
    
    // Simple serializable class to transfer move data
    public static class MoveData implements Serializable {
        private static final long serialVersionUID = 1L;
        public int startCol;
        public int startRow;
        public int targetCol;
        public int targetRow;
        public String moveType;
        
        public MoveData(int startCol, int startRow, int targetCol, int targetRow, String moveType) {
            this.startCol = startCol;
            this.startRow = startRow;
            this.targetCol = targetCol;
            this.targetRow = targetRow;
            this.moveType = moveType;
        }
    }
    
    // Add a keep-alive mechanism to prevent connection timeout
    private void startKeepAlive() {
        // Stop any existing keep-alive thread
        stopKeepAlive();
        
        // Start a new keep-alive thread
        keepAliveThread = new Thread(() -> {
            try {
                int missedPongs = 0;
                while (connected && socket != null && !socket.isClosed()) {
                    try {
                        // Send a ping every 5 seconds
                        Thread.sleep(5000);
                        
                        if (connected && socket != null && !socket.isClosed() && out != null) {
                            boolean pongReceived = false;
                            
                            // Send ping with proper synchronization
                            synchronized (out) {
                                out.writeObject("PING");
                                out.flush();
                                out.reset();
                                LOG.log(Level.FINE, "Sent keep-alive ping");
                            }
                            
                            // Wait for PONG response with timeout
                            long startTime = System.currentTimeMillis();
                            long timeoutMillis = 3000; // 3 second timeout
                            
                            while (System.currentTimeMillis() - startTime < timeoutMillis && !pongReceived) {
                                try {
                                    synchronized (in) {
                                        socket.setSoTimeout(1000); // 1 second read timeout
                                        Object response = in.readObject();
                                        if (response instanceof String) {
                                            String message = (String) response;
                                            if ("PONG".equals(message)) {
                                                pongReceived = true;
                                                missedPongs = 0;
                                                LOG.log(Level.FINE, "Received PONG response");
                                                break;
                                            } else if ("PING".equals(message)) {
                                                // If we receive a PING while waiting for PONG,
                                                // respond immediately and continue waiting
                                                synchronized (out) {
                                                    out.writeObject("PONG");
                                                    out.flush();
                                                    out.reset();
                                                    LOG.log(Level.FINE, "Sent PONG response while waiting");
                                                }
                                            }
                                            // For any other message, ignore and keep waiting
                                        }
                                    }
                                } catch (SocketTimeoutException e) {
                                    // Normal timeout, continue waiting if we still have time
                                    continue;
                                } catch (Exception e) {
                                    LOG.log(Level.WARNING, "Error while waiting for PONG: {0}", e.getMessage());
                                    break;
                                }
                            }
                            
                            // Reset socket timeout
                            socket.setSoTimeout(0);
                            
                            if (!pongReceived) {
                                missedPongs++;
                                LOG.log(Level.WARNING, "No PONG response received, missed count: {0}", missedPongs);
                                if (missedPongs >= 3) {
                                    LOG.log(Level.SEVERE, "Lost connection - no PONG responses");
                                    handleDisconnect();
                                    break;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        // Thread interrupted, exit
                        break;
                    } catch (IOException e) {
                        LOG.log(Level.WARNING, "Keep-alive failed: {0}", e.getMessage());
                        missedPongs++;
                        if (missedPongs >= 3 || !connected) {
                            handleDisconnect();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Keep-alive thread error: {0}", e.getMessage());
            } finally {
                try {
                    socket.setSoTimeout(0); // Make sure to reset timeout
                } catch (Exception e) {
                    // Ignore
                }
            }
            LOG.log(Level.INFO, "Keep-alive thread stopped");
        });
        
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();
        LOG.log(Level.INFO, "Keep-alive thread started");
    }
    
    private void stopKeepAlive() {
        if (keepAliveThread != null && keepAliveThread.isAlive()) {
            keepAliveThread.interrupt();
            try {
                keepAliveThread.join(1000); // Wait up to 1 second for thread to end
            } catch (InterruptedException e) {
                // Ignore
            }
            keepAliveThread = null;
        }
    }
}
