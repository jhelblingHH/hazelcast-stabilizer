package com.hazelcast.stabilizer.common.messaging;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@MessageSpec("useHeap")
public class UseAllMemoryMessage extends RunnableMessage {
    private final static Logger log = Logger.getLogger(UseAllMemoryMessage.class);

    private final int bufferSize = 1000;
    private final int delay;

    private static List list = new ArrayList();

    public UseAllMemoryMessage(MessageAddress messageAddress, int delay) {
        super(messageAddress);
        this.delay = delay;
    }

    public UseAllMemoryMessage(MessageAddress messageAddress) {
        this(messageAddress, 0);
    }

    @Override
    public void run() {
        new Thread() {
            @Override
            public void run() {
                log.debug("Starting a thread to consume all memory");
                for (;;) {
                    try {
                        allocateMemory();
                    } catch (OutOfMemoryError ex) {
                        //ignore
                    }
                }
            }

            private void allocateMemory() {
                while (!interrupted()) {
                    byte[] buff = new byte[bufferSize];
                    list.add(buff);
                    sleepMs(delay);
                }
            }

            private void sleepMs(int delay) {
                try {
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException e) {
                    log.warn("Interrupted during sleep.");
                    Thread.currentThread().interrupt();
                }
            }
        }.start();
    }
}