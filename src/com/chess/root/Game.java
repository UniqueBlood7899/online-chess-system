package com.chess.root;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;
import com.chess.application.GameController;
import com.chess.model.Mode;
import com.chess.model.Setting;
import com.chess.network.NetworkManager;
import com.chess.root.moves.Move;
import javafx.application.Platform;

public class Game {
	
	private GameController controller;
	private Board board;
	private Player whitePlayer;
	private Player blackPlayer;
	private Player currentPlayer;
	private Double moveCounter = 1.0;
	private boolean gameEnded = false;
	private static final Logger LOG = Logger.getLogger(String.class.getName());
	private String pgnEvent;
	private String pgnSite;
	private String pgnDate;
	private static int pgnRoundCounter = 0;
	private String pgnRound;
	private String pgnDifficulty;
	private String pgnWhite;
	private String pgnBlack;
	private String pgnResult = "*";
	private NetworkManager networkManager;
	private boolean isOnlineGame;

	public Game(GameController controller, Setting settings) {
		this.controller = controller;
		setUpPlayers(settings.getColor(), settings.getMode(), settings.getFenPlayer());
		setImportData(settings);
		holdThreads();
		pgnDifficulty = settings.getDifficultyName();
		this.board = new Board(this, settings);
		
		board.executePgn(settings);
		controller.renderDisplay();
		releaseThreads();
	} 

	private void setImportData(Setting settings) {
		
		pgnEvent = PgnParser.getEvent(settings);
		pgnSite = PgnParser.getSite(settings);
		pgnDate = PgnParser.getDate(settings);
		setRoundCounter(settings);
		String round = PgnParser.getRound(settings);
		if (round == null) {
			round = Integer.toString(pgnRoundCounter);
		} 
		pgnRound = round;
		pgnWhite = settings.getWhite();
		pgnBlack = settings.getBlack();
		pgnResult = PgnParser.getResult(settings); 
		if (settings.hasFen()) {
			moveCounter = settings.getFenMoveCounter();
			currentPlayer = settings.getFenPlayer() ? blackPlayer : whitePlayer;
		}
		
	}
	
	private static void setRoundCounter(Setting settings) {
		String round = PgnParser.getRound(settings);
		if (round == null) {
			pgnRoundCounter++;
		} else {
			try {
				int x = Integer.parseInt(round);
				pgnRoundCounter = x;
			} catch (Exception e) {
				pgnRoundCounter++;
			}
		}
	}
	
	// ---------------------------------- PLAYER HANDLING ----------------------------------

	
	public String getEvent() {
		if (pgnEvent == null) {
			return "";
		}
		return pgnEvent;
	}
	public String getSite() {
		if (pgnSite == null) {
			return "";
		}
		return pgnSite;
	}
	public String getDate() {
		if (pgnDate == null) {
			return "";
		}
		return pgnDate;
	}
	public String getRound() {
		if (pgnRound == null) {
			return "";
		}
		return pgnRound;
	}
	
	public String getDifficulty() {
		return pgnDifficulty;
	}
	
	public String getWhite() {
		if (pgnWhite == null) {
			return "";
		}
		return pgnWhite;
	}
	public String getBlack() {
		if (pgnBlack == null) {
			return "";
		}
		return pgnBlack;
	}
	public String getResult() {
		if (board.hasHistory()) {
			pgnResult = board.getHistory().get(getHistory().size()-1).getResult();
		} else if (pgnResult == null) {
			return "";
		}
		return pgnResult;
	}
	public List<Move> getHistory() {
		return board.getHistory();
	}

	public Player getPlayer() {
		return currentPlayer;
	}
	
	public Player getPlayer(boolean isBlack) {
		if(isBlack) {
			return blackPlayer;
		}
		return whitePlayer;
	}
	
	public Player getOtherPlayer() {
		if (currentPlayer != null && currentPlayer.equals(blackPlayer)) {
			return whitePlayer;
		}
		return blackPlayer;
	}

	public List<Player> getAIPlayers() {
		List<Player> players = new ArrayList<>();
		if (blackPlayer.isAI()) {
			players.add(blackPlayer);
		}
		if (whitePlayer.isAI()) {
			players.add(whitePlayer);
		}
		return players;
	}

	public void switchPlayer() {
		switchPlayerSilently();
		board.validateBoard();
		
		// Only start network listener if we're in online mode and it's the remote player's turn
		if (isOnlineGame) {
			boolean isHost = networkManager.isHost();
			boolean isRemoteTurn = (isHost && currentPlayer.equals(blackPlayer)) || 
			                     (!isHost && currentPlayer.equals(whitePlayer));
			
			// Start network listener only when it's the remote player's turn
			if (isRemoteTurn) {
				startNetworkListener();
			}
		} else {
			notifyAI();
		}
	}

	public void switchPlayerSilently() {
		currentPlayer = getOtherPlayer();
		
		if (controller != null) {
			controller.displayPlayer(this);
		}
	}

	public void setDummyMode(boolean on) {
		board.setDummyMode(on);
	}
	
	// ---------------------------------- EDIT MODE HANDLING ----------------------------------
	
	public void stepBack() {
		if (board.hasHistory()) {
			controller.setForwardBut(true);
			switchPlayerSilently();
			board.undoMove(null);
			decreaseMoveCounter();
			
			if (!board.hasHistory()) {
				controller.setBackBut(false);
				controller.requestFocusForward();
				controller.setDisplay(currentPlayer.toString() + " starts the game");
			}
			
			controller.setGoBut(true);
			//board.render();
			resetAI();
	
		}
	}

	public void stepForward() {
		if (board.hasFutureMoves()) {
			controller.setBackBut(true);
			board.redoMove(null);
		}
			
		if (!board.hasFutureMoves()) {
			controller.setForwardBut(false);
			controller.requestFocusBack();
			if (gameEnded) {
				controller.setGoBut(false);
			}
			
		}
	}
	
	private void holdThreads() {
		if (blackPlayer.isAI() && blackPlayer.getThread() != null) {
			blackPlayer.getThread().block(true);
		}
		if (whitePlayer.isAI() && whitePlayer.getThread() != null) {
			whitePlayer.getThread().block(true);
		}
		pauseCurrent();
	}
	
	public void pauseGame() {
		if (gameEnded) {
			controller.setGoBut(false);
		} else {
			controller.requestFocusGo();
		}
		if (board.hasHistory()) {
			controller.setBackBut(true);
		}
		board.setEditMode(true);
		holdThreads();
	}
	
	public void resumeGame() {
		gameEnded = false;
		controller.requestFocusStop();
		board.validateBoard();
		board.cleanUpEdit();
		board.setEditMode(false);
		
	
		releaseThreads();
	}
	
	private void releaseThreads() {
		if (blackPlayer.isAI() && blackPlayer.getThread() != null) {
			blackPlayer.getThread().block(false);
		}
		if (whitePlayer.isAI() && whitePlayer.getThread() != null) {
			whitePlayer.getThread().block(false);
		}
		resumeCurrent();
		notifyAI();
	}
	
	public void setSpeed(int speed) {
		board.setDelay(speed);
	}
	
	// ---------------------------------- THREAD HANDLING ----------------------------------
	
	private void notifyAI() {
		if (getOtherPlayer().isAI()) {
			getOtherPlayer().getThread().requestPause();
		}
		if (currentPlayer.isAI()) {
			currentPlayer.getThread().requestResume();
		}
	}
	
	private void resetAI() {
		if (whitePlayer.isAI() && whitePlayer.getThread().getState() == Thread.State.TERMINATED) {
			createThread(whitePlayer);
			whitePlayer.getThread().block(true);
		}
		if (blackPlayer.isAI() && blackPlayer.getThread().getState() == Thread.State.TERMINATED) {
			createThread(blackPlayer);
			blackPlayer.getThread().block(true);
		}
	}
	
	private void pauseCurrent() {
		if (currentPlayer.isAI()) {
			currentPlayer.getThread().requestPause();
		}
	}
	
	private void resumeCurrent() {
		if (currentPlayer.isAI()) {
			currentPlayer.getThread().requestResume();
		}
	}

	public void endGame(String endType, boolean hasWinner) {
		gameEnded = true;
		controller.setForwardBut(false);
		controller.setGoBut(false);
		if (blackPlayer.isAI() && blackPlayer.getThread() != null) {
			blackPlayer.getThread().requestStop();
		}
		if (whitePlayer.isAI() && whitePlayer.getThread() != null) {
			whitePlayer.getThread().requestStop();
		}
		LOG.log(Level.INFO, () -> "SYSTEM: ---------- game ends after " + moveCounter + " moves ----------");
		String end = endType;
		if (hasWinner) {
			end += " by " + getOtherPlayer().toString() + " player!";
		}
		controller.setDisplay(end);
	}

	// ---------------------------------- HELPER METHODS ----------------------------------

	public void updateMoveCounter() {
 		moveCounter += (Double) 0.5;
 		controller.updateMoveCounter(Double.toString(moveCounter));
 	}

	private void decreaseMoveCounter() {
 		moveCounter -= (Double) 0.5;
 		controller.updateMoveCounter(Double.toString(moveCounter));
 	}
	
	public Double getMoveCounter() {
		return moveCounter;
	}
	
	private void setUpPlayers(boolean color, Mode mode, boolean blackPlays) {
		blackPlayer = new Player(true);
   		whitePlayer = new Player(false);
   		currentPlayer = blackPlays ? blackPlayer : whitePlayer;
   		// checks mode value to set mode; for two manual players, no AI player has to be set up
   		if (mode == Mode.MANUAL_VS_AI) {
   			if (!color) {
   				createThread(whitePlayer);
   			} else {
   				createThread(blackPlayer);
   			}
   		} else if (mode == Mode.AI_ONLY) {
   			createThread(whitePlayer);
   			createThread(blackPlayer);
   		}
   		LOG.log(Level.INFO, "SYSTEM: ---------- game starts ----------");
	}

	private void createThread(Player player) {
		player.setAI();
		player.setThread(new AIThread(this, player));
		player.getThread().start();
	}

	public void setNetworkManager(NetworkManager manager) {
	    this.networkManager = manager;
	    this.isOnlineGame = true;
	    manager.setGame(this);
	    
	    // Always ensure host is white and client is black
	    boolean isHost = manager.isHost();
	    
	    if (isHost) {
	        // Host always plays as white
	        whitePlayer.setIsLocal(true);
	        blackPlayer.setIsLocal(false);
	        // First turn is white's (host's turn)
	        currentPlayer = whitePlayer;
	        board.setPlayerColor(false); // false = white plays
	        controller.setDisplay("White's turn (your move)");
	        
	        // Host doesn't need to listen initially since they go first
	    } else {
	        // Client always plays as black
	        whitePlayer.setIsLocal(false);
	        blackPlayer.setIsLocal(true);
	        // First turn is white's (opponent's turn)
	        currentPlayer = whitePlayer;
	        board.setPlayerColor(false); // false = white plays
	        controller.setDisplay("White's turn (opponent's move)");
	        
	        // Client needs to listen immediately for host's first move
	        startNetworkListener();
	    }
	}

	private void startNetworkListener() {
	    if (networkManager == null || !networkManager.isConnected()) {
	        LOG.log(Level.WARNING, "Cannot start network listener - no connection");
	        return;
	    }
	    
	    LOG.log(Level.INFO, "Starting network listener for opponent's move");
	    
	    new Thread(() -> {
	        boolean keepListening = true;
	        
	        while (keepListening && networkManager != null && networkManager.isConnected()) {
	            try {
	                // Wait for a move from the opponent
	                final Move move = networkManager.receiveMove();
	                
	                if (move != null) {
	                    Platform.runLater(() -> {
	                        try {
	                            LOG.log(Level.INFO, "Received opponent's move: {0}", move.getNotation());
	                            
	                            // Execute the opponent's move on our board WITHOUT sending it back
	                            board.executeMove(move);
	                            
	                            // Add endMove to update the move counter
	                            board.endMove();
	                            
	                            // Switch players (this should NOT trigger another network listener)
	                            switchPlayerAfterRemoteMove();
	                            
	                            // Now it's our turn, update the UI accordingly
	                            String displayText = currentPlayer.toString() + "'s turn (your move)";
	                            controller.setDisplay(displayText);
	                        } catch (Exception e) {
	                            LOG.log(Level.SEVERE, "Error processing received move: {0}", e.getMessage());
	                            controller.setDisplay("Error processing move: " + e.getMessage());
	                        }
	                    });
	                    
	                    // Stop listening after receiving one move - we'll start again after our move
	                    keepListening = false;
	                } else {
	                    LOG.log(Level.WARNING, "Null move received, will retry");
	                    // Short delay before retrying
	                    Thread.sleep(1000);
	                }
	            } catch (InterruptedException e) {
	                LOG.log(Level.WARNING, "Network listener interrupted");
	                keepListening = false;
	            } catch (Exception e) {
	                LOG.log(Level.SEVERE, "Error in network listener: {0}", e.getMessage());
	                try {
	                    // Short delay before retrying
	                    Thread.sleep(1000);
	                } catch (InterruptedException ie) {
	                    keepListening = false;
	                }
	            }
	        }
	        
	        LOG.log(Level.INFO, "Network listener stopped");
	    }).start();
	}

	private void switchPlayerAfterRemoteMove() {
	    currentPlayer = getOtherPlayer();
	    board.validateBoard();
	    controller.displayPlayer(this);
	}

	public void executeNetworkMove(Move move) {
	    // First execute move locally regardless of network status
	    try {
	        LOG.log(Level.INFO, "Executing local move: {0}", move.getNotation());
	        board.executeMove(move);
	        board.endMove();
	    } catch (Exception e) {
	        LOG.log(Level.SEVERE, "Failed to execute move locally: {0}", e.getMessage());
	        controller.setDisplay("Error: " + e.getMessage());
	        return;
	    }
	    
	    // Then handle network if this is an online game
	    if (networkManager != null && networkManager.isConnected()) {
	        try {
	            // Send move to opponent
	            boolean sendSuccessful = true;
	            try {
	                networkManager.sendMove(move);
	            } catch (Exception e) {
	                LOG.log(Level.SEVERE, "Failed to send move: {0}", e.getMessage());
	                controller.setDisplay("Move executed locally, but failed to send to opponent: " + e.getMessage());
	                sendSuccessful = false;
	            }
	            
	            // Switch to opponent's turn
	            currentPlayer = getOtherPlayer();
	            board.validateBoard();
	            
	            // Update UI to show it's opponent's turn
	            String displayText;
	            if (sendSuccessful) {
	                displayText = currentPlayer.toString() + "'s turn (opponent's move)";
	                controller.setDisplay(displayText);
	                
	                // Start listening for opponent's move ONLY if send was successful
	                startNetworkListener();
	            } else {
	                displayText = "Network error - move executed locally but not sent to opponent";
	                controller.setDisplay(displayText);
	                
	                // Try to recover the connection
	                new Thread(() -> {
	                    try {
	                        Thread.sleep(2000); // Wait briefly
	                        if (networkManager != null && networkManager.isConnected()) {
	                            // Try listening again if we're still connected
	                            startNetworkListener();
	                        }
	                    } catch (InterruptedException ie) {
	                        // Ignore
	                    }
	                }).start();
	            }
	        } catch (Exception e) {
	            LOG.log(Level.SEVERE, "Failed to process network move: {0}", e.getMessage());
	            controller.setDisplay("Move executed, but network error occurred: " + e.getMessage());
	        }
	    } else {
	        // For completeness, handle the case where this method is called for a non-network game
	        // We've already executed the move at the start of this method
	        LOG.log(Level.INFO, "Move executed in non-network game");
	    }
	}

	public boolean isOnlineGame() {
		return isOnlineGame;
	}
	
	public boolean canPlayerMove() {
	    if (!isOnlineGame) {
	        // In non-online games, human players can move during their turn
	        return !currentPlayer.isAI();
	    } else {
	        // In online games, determine if it's this player's turn
	        boolean isHost = networkManager.isHost();
	        boolean isLocalTurn;
	        
	        if (isHost) {
	            // Host plays as white
	            isLocalTurn = currentPlayer.equals(whitePlayer);
	        } else {
	            // Client plays as black
	            isLocalTurn = currentPlayer.equals(blackPlayer);
	        }
	        
	        // Can only move if it's the local player's turn
	        return isLocalTurn;
	    }
	}

	private boolean blackPlays() {
		return currentPlayer.equals(blackPlayer);
	}
	
	public NetworkManager getNetworkManager() {
	    return networkManager;
	}
	
	// ---------------------------------- GENERIC GETTERS ----------------------------------
	
	public GameController getController() {
		return controller;
	}

 	public Board getBoard() {
 		return board;
 	}

	// Add this method to the Game class to handle network disconnection
	public void handleDisconnection() {
	    Platform.runLater(() -> {
	        isOnlineGame = false;
	        
	        // Show disconnection message to user
	        controller.setDisplay("Opponent disconnected. Game ended.");
	        
	        // End the game without a winner
	        gameEnded = true;
	        controller.setGoBut(false);
	        controller.setForwardBut(false);
	        
	        // Log the disconnection
	        LOG.log(Level.INFO, "SYSTEM: ---------- game ends due to disconnection ----------");
	        
	        // Clean up network resources
	        try {
	            if (networkManager != null) {
	                networkManager.close();
	                networkManager = null;
	            }
	        } catch (IOException e) {
	            LOG.log(Level.SEVERE, "Error closing network connection: {0}", e.getMessage());
	        }
	    });
	}

}
