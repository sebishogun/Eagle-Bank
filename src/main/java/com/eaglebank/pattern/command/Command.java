package com.eaglebank.pattern.command;

public interface Command<T> {
    
    T execute();
    
    void undo();
    
    boolean canUndo();
    
    String getDescription();
}