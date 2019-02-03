package com.example.tenbillionfiles.config;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "file")
@Getter @Setter
public class StorageConfigurations {

    // must be power of 2 to permit bitwise calculation for getPartition function from PartitioningService
    public static final int PARTITIONS_NUMBER = 16;

    private @NotNull String storageDrive;
    private @NonNull String storageDir;

    private @NonNull Integer executorCorePoolSize;
    private @NotNull Integer executorMaxPoolSize;
    private @NotNull Integer executorQueueCapacity;
    private @NotNull String executorThreadNamePrefix;

    private @NonNull String indexDir;

}
