package com.chess.model;

public enum Mode {
    MANUAL_VS_AI("vs AI"),
    MANUAL_ONLY("vs Friend"),
    ONLINE_MULTIPLAYER("Online Multiplayer"),
    AI_ONLY("AI vs AI");
    
    private String text;
    
    Mode(String text) {
        this.text = text;
    }
    
    public String get() {
        return text;
    }
}
