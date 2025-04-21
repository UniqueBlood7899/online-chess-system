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
            LOG.log(Level.INFO, "Connected to host successfully");
            return true;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to connect to host: {0}", e.getMessage());
            return false;
        }
    }
    
    private void setupStreams() throws IOException {
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        LOG.log(Level.INFO, "Network streams established");
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
            out.writeObject(moveData);
            out.flush();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to send move: {0}", e.getMessage());
            handleDisconnect();
        }
    }
    
    public Move receiveMove() {
        try {
            LOG.log(Level.INFO, "Waiting for move...");
            Object receivedObj = in.readObject();
            
            if (receivedObj instanceof MoveData) {
                MoveData moveData = (MoveData)receivedObj;
                LOG.log(Level.INFO, "Received move data: {0},{1} -> {2},{3}", 
                    new Object[]{moveData.startCol, moveData.startRow, moveData.targetCol, moveData.targetRow});
                
                // Find the fields on the board
                Board board = game.getBoard();
                Field startField = board.getField(moveData.startCol, moveData.startRow);
                Field targetField = board.getField(moveData.targetCol, moveData.targetRow);
                
                // Get pieces
                Piece piece = startField.getPiece();
                Piece victim = targetField.getPiece();
                
                // If piece is null (already moved), find a piece of that type that can move to the target
                if (piece == null) {
                    for (Piece p : board.getPieces(!isHost)) { // Host is white, client is black
                        if (p.canMoveTo(targetField)) {
                            piece = p;
                            break;
                        }
                    }
                }
                
                // Create the move based on the data
                Move move = board.createMove(moveData.moveType, piece, targetField, victim);
                return move;
            } else {
                LOG.log(Level.SEVERE, "Received object is not a MoveData");
                return null;
            }
        } catch (IOException | ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Failed to receive move: {0}", e.getMessage());
            handleDisconnect();
            return null;
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
        if (out != null) out.close();
        if (in != null) in.close();
        if (socket != null) socket.close();
        if (serverSocket != null) serverSocket.close();
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
}
