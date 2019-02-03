package com.example.tenbillionfiles.services.partioning.tasks;

import java.util.concurrent.CountDownLatch;

/**
 * Generic partition related task
 *
 *  * @param <S> S
 *  * @param <T> T
 */

public interface PartitionTask<T, S> extends Cloneable {
    T process (S input, CountDownLatch latch);
    int getPartition();
    void setPartition(int partition );
    PartitionTask clone() throws CloneNotSupportedException;
}
