package com.example.processmanager.model;

import com.example.processmanager.model.ProcessInfo;

import java.util.ArrayList;
import java.util.List;

public class ProcessTreeNode {

    private final ProcessInfo process;

    private final List<ProcessTreeNode> children =
            new ArrayList<>();

    public ProcessTreeNode(ProcessInfo process) {
        this.process = process;
    }

    public ProcessInfo getProcess() {
        return process;
    }

    public List<ProcessTreeNode> getChildren() {
        return children;
    }

    public void addChild(ProcessTreeNode child) {
        children.add(child);
    }
}