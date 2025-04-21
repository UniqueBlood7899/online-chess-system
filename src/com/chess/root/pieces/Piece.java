package com.chess.root.pieces;

import java.io.Serializable;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.chess.root.Board;
import com.chess.root.Field;
import com.chess.root.moves.Move;
import com.chess.model.Direction;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public abstract class Piece implements Serializable {
    // Add serialVersionUID for serialization compatibility
    private static final long serialVersionUID = 1L;
    
    // Mark transient fields that shouldn't be serialized
    protected transient Board board;
    protected transient Field field;
    private String descriptiveName; // internal use only
    private String notation;
    protected String fen;
    private transient ImageView symbol;
    private transient Image image;
    protected boolean color;
    protected int rating;
    protected int posValue;
    protected int defense;
    private static final int IMGSIZE = 60;
    protected int[][] table;
    
    // Add these methods for custom serialization
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // Write the field's position instead of the field object
        if (field != null) {
            out.writeInt(field.getColumn());
            out.writeInt(field.getRow());
        } else {
            out.writeInt(-1);
            out.writeInt(-1);
        }
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Read the field position (will be restored later)
        int col = in.readInt();
        int row = in.readInt();
        // Field and board will be set later when piece is re-added to board
    }
    
    // Add a method to restore board reference after deserialization
    public void restoreBoard(Board board) {
        this.board = board;
    }
    
    // Add a method to restore field reference after deserialization
    public void restoreField(Field field) {
        this.field = field;
    }
    
    public Piece(Board board, Field field, boolean color, String name, String notation, int rating, int[][] table, boolean simulation) {
        this.board = board;
        if (field == null) {
            throw new NullPointerException("no field set!");
        }
        this.field = field;
        this.descriptiveName = name;
        this.notation = notation;
        this.color = color;
        this.rating = rating;
        this.table = table;
        
        if (!simulation) {
            createSymbol();
            init();
        }
    }
    
    public String getFen() {
        return fen;
    }
    
    public String getCastlingFen() {
        return "";
    }
    
    public void init() {
        field.setPiece(this, true);
        board.addPiece(this);
    }
    
    // ---------------------------------- GAMEPLAY HADNLING ----------------------------------
    
    public void setField(Field field) {
        if (field == null) {
            throw new NullPointerException("null value was set" + this.toString());
        }
        this.field = field;
        updatePos();
        board.endMove();
    }
    
    public void setFieldSilently(Field field) {
        this.field = field;
        updatePos();
    }
    
    private void updatePos() {
        posValue = table[field.getRow()][field.getColumn()];
    }
    
    // ---------------------------------- GENERIC GETTERS AND SETTERS ----------------------------------
    
    public Field getField() {
        if (field == null) {
            throw new NullPointerException("Field not found! " + this.toString());
        }
        return field;
    }

    public int getColumn() { 
        if (field == null) {
            throw new NullPointerException("Field column not found - out of range!" + this.toString());
        }
        return field.getColumn();
    }

    public int getRow() {
        if (field == null) {
            throw new NullPointerException("Field row not found - out of range!");
        }
        return field.getRow();
    }
        
    public boolean isBlack() {
        return color;
    }

    public boolean getColor() {
        return color;
    }

    public ImageView getSymbol() {
        return symbol;
    }
    
    public String getNotation() {
        return notation;
    }
    
    public String getPgnNotation() {
        return getNotation();
    }
    
    public int getRating() {
        return rating;
    }
    
    public int getValue() {
        return rating + posValue;
    }
    
    public boolean wasMoved() {
        return true;
    }
    
    public void moved() {
        
    }
    
    public void unmove() {
        
    }
    
    public boolean isDead() {
        return false;
    }
    
    public void setEndTable(boolean end) {
        
    }

    // ---------------------------------- HELPER METHODS ----------------------------------
    
    public void createSymbol() {
        if (image == null || symbol == null) {
            String path = "com/chess/resources/img/";
            path += descriptiveName;
            path += this.isBlack() ? "_b.png" : "_w.png";
            final String filePath = path;
            ImageView img = new ImageView(filePath);
            img.setFitWidth(IMGSIZE);
            img.setFitHeight(IMGSIZE);
            symbol = img;
            
            Double imgSize = (double) IMGSIZE;
            image = new Image(filePath, imgSize, imgSize, false, false);
        }
    }
    
    public Image getImage() {
        return image;
    }

    @Override
    public String toString() {
        return descriptiveName + "" + field.getNotation();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            if ((this instanceof PawnPiece && obj instanceof PawnPiece) || (this instanceof RookPiece && obj instanceof RookPiece) || (this instanceof KnightPiece && obj instanceof KnightPiece)) {
                return true;
            }
            if ((this instanceof BishopPiece && obj instanceof BishopPiece) || (this instanceof QueenPiece && obj instanceof QueenPiece) || (this instanceof KingPiece && obj instanceof KingPiece)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        int hash = this.isBlack() ? 2 : 0;
        hash += this.getField().getColumn();
        hash += this.getField().getRow();
        hash += this.toString().hashCode();
        hash += 123;
        return hash;
    }
    
    // ---------------------------------- ABSTRACT METHODS ----------------------------------
    
    public abstract List<Move> getMoves();

    public void initializeFenCastling(String cas) {
    }

    public boolean canMoveTo(Field targetField) {
        // Check if this piece can move to the target field
        List<Move> availableMoves = this.getMoves();
        if (availableMoves != null) {
            for (Move move : availableMoves) {
                if (move.getField().equals(targetField)) {
                    return true;
                }
            }
        }
        return false;
    }
}
