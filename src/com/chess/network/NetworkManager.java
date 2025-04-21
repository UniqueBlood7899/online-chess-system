package com.chess.network;

import java.io.*;
import java.net.*;
import com.chess.root.moves.Move;
import com.chess.model.Setting;
import com.chess.root.Game;
import com.chess.root.Board; // Add this import
import com.chess.root.pieces.Piece;
import com.chess.root.Field;
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
            // Create a simplified version of the move for network transmission
            MoveDTO moveDTO = new MoveDTO(move);
            LOG.log(Level.INFO, "Sending move: {0}", move.getNotation());
            out.writeObject(moveDTO);
            out.flush();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to send move: {0}", e.getMessage());
            handleDisconnect();
        }
    }
    
    public Move receiveMove() {
        try {
            LOG.log(Level.INFO, "Waiting for move...");
            MoveDTO moveDTO = (MoveDTO) in.readObject();
            LOG.log(Level.INFO, "Received move DTO");
            
            // Convert DTO back to Move object
            if (game != null) {
                Move move = moveDTO.toMove(game.getBoard());
                LOG.log(Level.INFO, "Received move: {0}", move.getNotation());
                return move;
            } else {
                LOG.log(Level.SEVERE, "Game reference is null, cannot convert MoveDTO to Move");
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
    
    // Inner class for Move Data Transfer Object
    public static class MoveDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        
        // Store only the necessary information about the move
        private int startCol;
        private int startRow;
        private int targetCol;
        private int targetRow;
        private int victimCol = -1;
        private int victimRow = -1;
        private String pieceType;
        private String moveType;
        private boolean isBlack;
        
        public MoveDTO(Move move) {
            // Record source position
            startCol = move.getStartField().getColumn();
            startRow = move.getStartField().getRow();
            
            // Record target position
            targetCol = move.getField().getColumn();
            targetRow = move.getField().getRow();
            
            // Record piece information
            Piece piece = move.getPiece();
            pieceType = piece.getClass().getSimpleName();
            isBlack = piece.isBlack();
            
            // Record victim information if any
            if (move.getVictim() != null) {
                victimCol = move.getVictimField().getColumn();
                victimRow = move.getVictimField().getRow();
            }
            
            // Record move type for special moves
            moveType = move.getClass().getSimpleName();
        }
        
        public Move toMove(Board board) {
            // Reconstruct the move from the serialized data
            Field startField = board.getField(startCol, startRow);
            Field targetField = board.getField(targetCol, targetRow);
            
            // Get the piece at the start position (might be null after a move)
            Piece piece = startField.getPiece();
            
            // For the receiving player, the piece might have already moved, so find it
            if (piece == null) {
                // Look through all pieces to find the one that should be at this start position
                for (boolean isBlack : new boolean[]{true, false}) {
                    for (Piece p : board.getPieces(isBlack)) {
                        if (p.getClass().getSimpleName().equals(pieceType) && 
                            p.isBlack() == this.isBlack) {
                            piece = p;
                            break;
                        }
                    }
                }
            }
            
            // Get the victim piece if any
            Piece victim = null;
            if (victimCol >= 0 && victimRow >= 0) {
                Field victimField = board.getField(victimCol, victimRow);
                victim = victimField.getPiece();
            }
            
            // Let the board handle the actual move creation and execution
            return board.createMove(pieceType, moveType, piece, targetField, victim);
        }
    }
}
