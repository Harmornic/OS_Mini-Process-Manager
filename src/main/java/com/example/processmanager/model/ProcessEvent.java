package com.example.processmanager.model;

import java.time.Instant;

public class ProcessEvent {

    public enum EventType {
        CREATED,
        TERMINATED
    }

    private final long pid;
    private final String processName;
    private final EventType eventType;
    private final Instant timestamp;

    public ProcessEvent(
            long pid,
            String processName,
            EventType eventType,
            Instant timestamp) {

        this.pid = pid;
        this.processName = processName;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    public long getPid() {
        return pid;
    }

    public String getProcessName() {
        return processName;
    }

    public EventType getEventType() {
        return eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}