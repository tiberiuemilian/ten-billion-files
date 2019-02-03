package com.example.tenbillionfiles.services.partioning;

import com.example.tenbillionfiles.config.StorageConfigurations;
import com.example.tenbillionfiles.services.partioning.results.ConsolidatedResult;
import com.example.tenbillionfiles.services.partioning.tasks.PartitionTask;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.example.tenbillionfiles.config.StorageConfigurations.PARTITIONS_NUMBER;

@Service
public class PartitioningService {

    private transient static final Log logger = LogFactory.getLog(PartitioningService.class);

    @Autowired
    private StorageConfigurations storageConfigurations;

    private static List<ReentrantLock> partitionLocks;

    private static Executor executor;

    @PostConstruct
    public void initExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(storageConfigurations.getExecutorCorePoolSize());
        threadPoolTaskExecutor.setMaxPoolSize(storageConfigurations.getExecutorMaxPoolSize());
        threadPoolTaskExecutor.setQueueCapacity(storageConfigurations.getExecutorQueueCapacity());
        threadPoolTaskExecutor.setThreadNamePrefix(storageConfigurations.getExecutorThreadNamePrefix());
        threadPoolTaskExecutor.initialize();
        executor = threadPoolTaskExecutor;
    }

    @PostConstruct
    public void initPartitionLocks() {
        partitionLocks = new ArrayList<>(PARTITIONS_NUMBER);
        for (int partition=0; partition<PARTITIONS_NUMBER; partition++) {
            partitionLocks.add(new ReentrantLock());
        }
    }

    public <S, R extends PartitionTask, T> void runOnAllPartitions(final S partitionTaskInput, final R task, final ConsolidatedResult<T> consolidatedResult, final long timeOut, final TimeUnit timeUnit) throws CloneNotSupportedException {
        Collection<PartitionTask<T, S>> tasks = new ArrayList<>(PARTITIONS_NUMBER);
        for (int partition=0; partition<PARTITIONS_NUMBER; partition++) {
            PartitionTask<T, S> agentTask = task.clone();
            agentTask.setPartition(partition);
            tasks.add(agentTask);
        }
        final CountDownLatch latch = new CountDownLatch(PARTITIONS_NUMBER);
        final List<CompletableFuture<T>> theFutures = tasks.stream()
                .map(partitionTask -> CompletableFuture.supplyAsync(() -> processPartitionTask(partitionTask, partitionTaskInput, latch), executor))
                .collect(Collectors.<CompletableFuture<T>>toList());

        final CompletableFuture<List<T>> allDone = collectPartitionTasks(theFutures);
        try {
//            latch.await(timeOut, timeUnit);
            logger.debug("complete... adding results");
            allDone.get().forEach(consolidatedResult::addResult);
        } catch (final InterruptedException | ExecutionException e) {
            logger.error("Thread Error", e);
            throw new RuntimeException("Thread Error, could not complete processing", e);
        }
    }

    private <E> CompletableFuture<List<E>> collectPartitionTasks(final List<CompletableFuture<E>> futures) {
        final CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        return allDoneFuture.thenApply(v -> futures.stream()
                .map(CompletableFuture<E>::join)
                .collect(Collectors.<E>toList())
        );
    }

    private <T, S> T processPartitionTask(final PartitionTask<T, S> partitionTask, final S searchTerm, final CountDownLatch latch) {
        logger.debug("Starting: " + partitionTask);
        T searchResults = null;
        try {
            searchResults = partitionTask.process(searchTerm, latch);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return searchResults;
    }

    public int getPartition(String fileName) {

        // simple way to find modulo of 2^i numbers using bitwise.
        // For cases 2^i, ( 2, 4, 8, 16 ...)
        // n % 2^i = n & (2^i - 1)
        return fileName.hashCode() & (PARTITIONS_NUMBER - 1);
    }

    public ReentrantLock getPartitionLock(int partition) {
        return partitionLocks.get(partition);
    }

}
