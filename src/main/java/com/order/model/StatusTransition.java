package com.order.model;

public class StatusTransition {
    private String fromStatus;
    private String toStatus;

    public StatusTransition(String fromStatus, String toStatus) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }
}
