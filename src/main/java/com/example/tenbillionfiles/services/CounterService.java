package com.example.tenbillionfiles.services;

import com.example.tenbillionfiles.exception.FileStorageException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import static com.example.tenbillionfiles.config.StorageConfigurations.PARTITIONS_NUMBER;

@Service
public class CounterService {

    @Autowired
    private FileStorageService fileStorageService;

    @Getter
    private static long fileCounter;

    @Getter
    private static ReentrantLock counterLock = new ReentrantLock();

    public void initFileCounter() {
        try {
            fileCounter = 0L;
            for (int partition = 0; partition< PARTITIONS_NUMBER; partition++) {
                Path partitionPath = fileStorageService.getStorageLocation(partition);

                // org.hyperic.sigar.Sigar().getDirStat(dir).getTotal() from http://support.hyperic.com
                // could be faster using JNI but is system dependent (Win/Linux ..32/64..)
                fileCounter += Files.list(partitionPath).count();
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not count files from partitions.", e);
        }
    }

    public long incrementFileCounter() {
        return ++fileCounter;
    }

    public long decrementFileCounter() {
        return --fileCounter;
    }

}
