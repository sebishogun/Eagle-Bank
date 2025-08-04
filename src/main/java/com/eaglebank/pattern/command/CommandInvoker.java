package com.eaglebank.pattern.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Stack;

@Slf4j
@Component
public class CommandInvoker {
    
    private final Stack<Command<?>> executedCommands = new Stack<>();
    private final Stack<Command<?>> undoneCommands = new Stack<>();
    
    public <T> T execute(Command<T> command) {
        log.info("Executing command: {}", command.getDescription());
        
        try {
            T result = command.execute();
            executedCommands.push(command);
            undoneCommands.clear(); // Clear redo stack on new command
            return result;
        } catch (Exception e) {
            log.error("Command execution failed: {}", e.getMessage());
            throw e;
        }
    }
    
    public void undo() {
        if (executedCommands.isEmpty()) {
            log.warn("No commands to undo");
            return;
        }
        
        Command<?> command = executedCommands.pop();
        if (!command.canUndo()) {
            log.warn("Command cannot be undone: {}", command.getDescription());
            executedCommands.push(command); // Put it back
            return;
        }
        
        log.info("Undoing command: {}", command.getDescription());
        try {
            command.undo();
            undoneCommands.push(command);
        } catch (Exception e) {
            log.error("Undo failed: {}", e.getMessage());
            executedCommands.push(command); // Put it back on failure
            throw e;
        }
    }
    
    public void redo() {
        if (undoneCommands.isEmpty()) {
            log.warn("No commands to redo");
            return;
        }
        
        Command<?> command = undoneCommands.pop();
        log.info("Redoing command: {}", command.getDescription());
        
        try {
            command.execute();
            executedCommands.push(command);
        } catch (Exception e) {
            log.error("Redo failed: {}", e.getMessage());
            undoneCommands.push(command); // Put it back on failure
            throw e;
        }
    }
    
    public void clearHistory() {
        executedCommands.clear();
        undoneCommands.clear();
        log.info("Command history cleared");
    }
    
    public int getExecutedCommandCount() {
        return executedCommands.size();
    }
    
    public int getUndoneCommandCount() {
        return undoneCommands.size();
    }
}