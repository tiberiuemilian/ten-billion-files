package com.example.tenbillionfiles.services;

import com.example.tenbillionfiles.config.StorageConfigurations;
import com.example.tenbillionfiles.exception.FileAlreadyExists;
import com.example.tenbillionfiles.exception.FileNotFoundException;
import com.example.tenbillionfiles.exception.FileStorageException;
import com.example.tenbillionfiles.services.partioning.PartitioningService;
import org.apache.lucene.index.IndexWriterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    @Autowired
    private StorageConfigurations storageConfigurations;

    @Autowired
    private PartitioningService partitioningService;

    @Autowired
    private RegexIndexService regexIndexService;

    @Autowired
    private LuceneIndexService luceneIndexService;

    @Autowired
    private CounterService counterService;

    public void initStorage() {
        try {
            for (int partition=0; partition<StorageConfigurations.PARTITIONS_NUMBER; partition++) {
                Files.createDirectories(getStorageLocation(partition));
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not create the directory where the uploaded files will be stored.", e);
        }
    }

    public String addFile(MultipartFile file) {
        // Normalize file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Check if the file's name contains invalid characters
            if(fileName.contains("..")) {
                throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
            }

            int partition = partitioningService.getPartition(fileName);
            Path targetLocation = getStorageLocation(partition).resolve(fileName);

            if (Files.exists(targetLocation)) {
                throw new FileAlreadyExists("File " + fileName +  " already exists");
            }

            ReentrantLock partitionLock = partitioningService.getPartitionLock(partition);
            ReentrantLock counterLock = counterService.getCounterLock();
            try {
                partitionLock.lock();
                counterLock.lock();
                // Copy file to the target location (Replacing existing file with the same name)
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                regexIndexService.indexDoc(fileName);
                luceneIndexService.indexDoc(fileName, IndexWriterConfig.OpenMode.CREATE);

                counterService.incrementFileCounter();
            } finally {
                partitionLock.unlock();
                counterLock.unlock();
            }

            return fileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    public String modifyFile(MultipartFile file) {
        // Normalize file name
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());

        try {
            // Check if the file's name contains invalid characters
            if(fileName.contains("..")) {
                throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
            }

            int partition = partitioningService.getPartition(fileName);
            // Copy file to the target location (Replacing existing file with the same name)
            Path targetLocation = getStorageLocation(partition).resolve(fileName);

            if (Files.notExists(targetLocation)) {
                throw new FileNotFoundException("File not found " + fileName);
            }

            ReentrantLock partitionLock = partitioningService.getPartitionLock(partition);
            try {
                partitionLock.lock();
                // Copy file to the target location (Replacing existing file with the same name)
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                regexIndexService.indexDoc(fileName);
                luceneIndexService.indexDoc(fileName, IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            } finally {
                partitionLock.unlock();
            }

            return fileName;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = getStorageLocation(partitioningService.getPartition(fileName)).resolve(fileName);;
            Resource resource = new UrlResource(filePath.toUri());
            if(resource.exists()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new FileNotFoundException("File not found " + fileName, ex);
        }
    }

    public void deleteFile(String fileName) {
        try {
            // Check if the file's name contains invalid characters
            if(fileName.contains("..")) {
                throw new FileStorageException("Sorry! Filename contains invalid path sequence " + fileName);
            }

            int partition = partitioningService.getPartition(fileName);
            Path targetLocation = getStorageLocation(partition).resolve(fileName);

            if (Files.notExists(targetLocation)) {
                throw new FileNotFoundException("File not found " + fileName);
            }

            ReentrantLock partitionLock = partitioningService.getPartitionLock(partition);
            ReentrantLock counterLock = counterService.getCounterLock();

            try {
                partitionLock.lock();
                counterLock.lock();
                // Delete the file
                Files.delete(targetLocation);
                regexIndexService.removeDocFromIndex(fileName);
                luceneIndexService.removeDocFromIndex(fileName);
                counterService.decrementFileCounter();
            } finally {
                partitionLock.unlock();
                counterLock.unlock();
            }

        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + fileName + ". Please try again!", ex);
        }
    }

    public List<String> luceneSearch(String query) {
        try {
            return luceneIndexService.multiThreadSearch(query);
        } catch (CloneNotSupportedException e) {
            logger.error("Exception calling lucene index service.",  e);
        }
        return null;
    }

    public List<String> regexSearch(String regex) {
        try {
            return regexIndexService.multiThreadSearch(regex);
        } catch (CloneNotSupportedException e) {
            logger.error("Exception calling regex index service.",  e);
        }
        return null;
    }

    public Path getStorageLocation(int partition) {
        return Paths.get(storageConfigurations.getStorageDrive() + partition + File.separator
                                + storageConfigurations.getStorageDir()).toAbsolutePath().normalize();
    }

    public Path getIndexLocation(int partition) {
        return Paths.get(storageConfigurations.getStorageDrive() + partition + File.separator
                + storageConfigurations.getIndexDir()).toAbsolutePath().normalize();
    }

    public String getDownloadUri(String fileName) {
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/file/")
                .path(fileName)
                .toUriString();

        return fileDownloadUri;
    }

    public long count() {
        ReentrantLock counterLock = counterService.getCounterLock();
        try {
            counterLock.lock();
            return counterService.getFileCounter();
        } finally {
            counterLock.unlock();
        }
    }
}
