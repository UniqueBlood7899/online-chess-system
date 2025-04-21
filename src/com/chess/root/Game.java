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
	        // Make sure the first turn is white's
	        currentPlayer = whitePlayer;
	        board.setPlayerColor(false); // false = white plays
	        controller.setDisplay("White's turn (your move)");
	    } else {
	        // Client always plays as black
	        whitePlayer.setIsLocal(false);
	        blackPlayer.setIsLocal(true);
	        // Make sure the first turn is white's (opponent's turn)
	        currentPlayer = whitePlayer;
	        board.setPlayerColor(false); // false = white plays
	        controller.setDisplay("White's turn (opponent's move)");
	    }
	    
	    // Start network listener only after setup is complete
	    startNetworkListener();
	}

	private void startNetworkListener() {
	    if (networkManager != null && networkManager.isConnected()) {
	        new Thread(() -> {
	            try {
	                while (networkManager.isConnected()) {
	                    final Move move = networkManager.receiveMove();
	                    if (move != null) {
	                        Platform.runLater(() -> {
	                            try {
	                                // Execute the opponent's move on our board
	                                board.executeMove(move);
	                                
	                                // Complete the move process - this switches the current player
	                                board.endMove();
	                                
	                                // Update display to show it's now your turn
	                                String displayText = getPlayer().toString() + "'s turn";
	                                if ((networkManager.isHost() && getPlayer().equals(blackPlayer)) || 
	                                    (!networkManager.isHost() && getPlayer().equals(whitePlayer))) {
	                                    displayText += " (opponent's move)";
	                                } else {
	                                    displayText += " (your move)";
	                                }
	                                controller.setDisplay(displayText);
	                            } catch (Exception e) {
	                                LOG.log(Level.SEVERE, "Error processing received move: {0}", e.getMessage());
	                                controller.setDisplay("Error processing move: " + e.getMessage());
	                            }
	                        });
	                    } else {
	                        break; // Exit if null move received (error occurred)
	                    }
	                }
	            } catch (Exception e) {
	                Platform.runLater(() -> {
	                    controller.setDisplay("Connection error: " + e.getMessage());
	                });
	            }
	        }).start();
	    }
	}

	public void handleDisconnection() {
		Platform.runLater(() -> {
			controller.setDisplay("Opponent disconnected");
		});
	}

	public void executeNetworkMove(Move move) {
	    if (networkManager != null && networkManager.isConnected()) {
	        try {
	            // Execute move locally first
	            board.executeMove(move);
	            
	            // Send move to opponent
	            networkManager.sendMove(move);
	            
	            // Complete move process - this will call switchPlayer() 
	            // which changes currentPlayer to the opponent's player
	            board.endMove();
	            
	            // Update display to show whose turn it is
	            String displayText = getPlayer().toString() + "'s turn";
	            if ((networkManager.isHost() && getPlayer().equals(blackPlayer)) || 
	                (!networkManager.isHost() && getPlayer().equals(whitePlayer))) {
	                displayText += " (opponent's move)";
	            } else {
	                displayText += " (your move)";
	            }
	            controller.setDisplay(displayText);
	            
	        } catch (Exception e) {
	            LOG.log(Level.SEVERE, "Failed to send move: {0}", e.getMessage());
	            controller.setDisplay("Failed to send move: " + e.getMessage());
	        }
	    } else {
	        // Non-network move
	        board.executeMove(move);
	        board.endMove();
	    }
	}

	public boolean isOnlineGame() {
		return isOnlineGame;
	}
	
	// Replace the canPlayerMove method with this correct version
	public boolean canPlayerMove() {
	    if (!isOnlineGame) {
	        // In non-online games, human players can move during their turn
	        return !currentPlayer.isAI();
	    } else {
	        // In online games, determine if it's this player's turn
	        boolean isHost = networkManager.isHost();
	        boolean isWhiteTurn = currentPlayer.equals(whitePlayer);
	        
	        // Host plays white, client plays black
	        return (isHost && isWhiteTurn) || (!isHost && !isWhiteTurn);
	    }
	}

	// Add this helper method
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


}
