package com.example.processmanager;

import com.example.processmanager.model.ProcessInfo;
import com.example.processmanager.service.ProcessService;

import java.util.List;

public class ProcessManagerDemo {

    public static void main(String[] args) {

        ProcessService processService = new ProcessService();

        List<ProcessInfo> processes = processService.getAllProcesses();

        System.out.println("=== Process Manager Data ===");

        for (ProcessInfo process : processes) {
            System.out.println(
                    process.getPid()
                            + " | "
                            + process.getName()
                            + " | "
                            + process.getStatus()
                            + " | "
                            + process.getStartTime()
            );
        }
    }
}