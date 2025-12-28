package com.order.service;

public class OrderStateMachine {
    private String currentState;

    public OrderStateMachine(String initialState) {
        this.currentState = initialState;
    }

    public String getCurrentState() {
        return currentState;
    }

    public void transitionTo(String newState) {
        // Logic for state transition
        this.currentState = newState;
    }
}
