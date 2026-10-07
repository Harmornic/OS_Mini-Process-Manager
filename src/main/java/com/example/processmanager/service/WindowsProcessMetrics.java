package com.example.processmanager.service;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;

import java.time.Duration;

public class WindowsProcessMetrics {

    public Duration getCpuTime(long pid) {

        WinNT.HANDLE processHandle = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_LIMITED_INFORMATION,
                false,
                (int) pid
        );

        if (processHandle == null) {
            return Duration.ZERO;
        }

        try {
            WinBase.FILETIME creationTime = new WinBase.FILETIME();
            WinBase.FILETIME exitTime = new WinBase.FILETIME();
            WinBase.FILETIME kernelTime = new WinBase.FILETIME();
            WinBase.FILETIME userTime = new WinBase.FILETIME();

            boolean success = Kernel32.INSTANCE.GetProcessTimes(
                    processHandle,
                    creationTime,
                    exitTime,
                    kernelTime,
                    userTime
            );

            if (!success) {
                return Duration.ZERO;
            }

            long kernel100ns = fileTimeToLong(kernelTime);
            long user100ns = fileTimeToLong(userTime);

            long total100ns = kernel100ns + user100ns;

            return Duration.ofNanos(total100ns * 100);

        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle);
        }
    }

    private long fileTimeToLong(WinBase.FILETIME fileTime) {
        return fileTime.dwLowDateTime & 0xffffffffL
                | ((long) fileTime.dwHighDateTime << 32);
    }
}