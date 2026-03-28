package com.aquiles.nexusrevive.scheduler;

@FunctionalInterface
public interface NexusTask {
    NexusTask NOOP = () -> {
    };

    void cancel();
}
