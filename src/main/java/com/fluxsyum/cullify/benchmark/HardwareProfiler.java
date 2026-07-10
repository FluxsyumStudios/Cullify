package com.fluxsyum.cullify.benchmark;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

public class HardwareProfiler {

    public static String getOS() {
        return System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ") " + System.getProperty("os.version");
    }

    public static String getCPU() {
        String cpuIdent = System.getenv("PROCESSOR_IDENTIFIER");
        if (cpuIdent == null) cpuIdent = "Unknown CPU";
        int cores = Runtime.getRuntime().availableProcessors();
        return cpuIdent + " [" + cores + " threads]";
    }

    public static String getRAM() {
        Runtime r = Runtime.getRuntime();
        long max = r.maxMemory() / (1024 * 1024);
        long total = r.totalMemory() / (1024 * 1024);
        long free = r.freeMemory() / (1024 * 1024);
        long used = total - free;
        return used + "MB used / " + max + "MB max";
    }

    public static String getGPU() {
        try {
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            String version = GL11.glGetString(GL11.GL_VERSION);
            if (vendor == null) return "Unknown GPU (No GL Context)";
            return vendor + " - " + renderer + " [" + version + "]";
        } catch (Exception e) {
            return "Unknown GPU (Error reading GL)";
        }
    }
    
    public static String getJavaVersion() {
        return System.getProperty("java.version") + " " + System.getProperty("java.vendor");
    }
}
