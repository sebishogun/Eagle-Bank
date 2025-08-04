package com.eaglebank.pattern.chain;

public abstract class ValidationHandler<T> {
    
    private ValidationHandler<T> nextHandler;
    
    public ValidationHandler<T> setNext(ValidationHandler<T> handler) {
        this.nextHandler = handler;
        return handler;
    }
    
    public void validate(T request) {
        if (canHandle(request)) {
            doValidate(request);
        }
        
        if (nextHandler != null) {
            nextHandler.validate(request);
        }
    }
    
    protected abstract boolean canHandle(T request);
    
    protected abstract void doValidate(T request);
}