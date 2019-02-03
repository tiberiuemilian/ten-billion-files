package com.example.tenbillionfiles.services;

import com.example.tenbillionfiles.exception.FileStorageException;
import com.example.tenbillionfiles.services.partioning.PartitioningService;
import com.example.tenbillionfiles.services.partioning.results.SearchResults;
import com.example.tenbillionfiles.services.partioning.tasks.RegexSearchTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.tenbillionfiles.config.StorageConfigurations.PARTITIONS_NUMBER;

@Service
public class RegexIndexService {

    private static final Logger logger = LoggerFactory.getLogger(RegexIndexService.class);

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private PartitioningService partitioningService;

    private List<List<String>> regexIndexes;

    public void initIndexes() {
        try {
            logger.info("Start regex-indexes initialization.");
            regexIndexes = new ArrayList<>(PARTITIONS_NUMBER);
            for (int partition = 0; partition< PARTITIONS_NUMBER; partition++) {
                logger.debug("Indexing partition {}", partition);
                LinkedList<String> regexIndex = new LinkedList<>();
                Path partitionPath = fileStorageService.getStorageLocation(partition);
                logger.debug("Partition path for partition {} is: '{}'", partition, partitionPath.toAbsolutePath());
                indexDocs(regexIndex, partitionPath);
                regexIndexes.add(regexIndex);
            }
            logger.info("End regex-indexes initialization.");
        } catch (IOException e) {
            throw new FileStorageException("Could not create regex indexes.", e);
        }
    }

    public void indexDoc(String fileName) {
        List<String> regexIndex = regexIndexes.get(partitioningService.getPartition(fileName));
        if (!regexIndex.contains(fileName)) {
            regexIndex.add(fileName);
        }
    }

    public void removeDocFromIndex(String fileName) {
        List<String> regexIndex = regexIndexes.get(partitioningService.getPartition(fileName));
        if (regexIndex.contains(fileName)) {
            regexIndex.remove(fileName);
        }
    }

    /**
     * single threaded
     */
    public List<String> search(String regex) {
        List<String> findings = new LinkedList<>();
        Pattern pattern = Pattern.compile(regex);
        for (List<String> index : regexIndexes) {
            findings.addAll(searchInIndex(index, pattern));
        }
        return findings;
    }

    /**
     * multithreaded search
     * @param regex
     * @return
     * @throws CloneNotSupportedException
     */

    public List<String> multiThreadSearch(String regex) throws CloneNotSupportedException {
        SearchResults searchResults = new SearchResults();
        partitioningService.runOnAllPartitions(regex, new RegexSearchTask(this), searchResults, 60, TimeUnit.SECONDS);
        return searchResults.getResults();
    }

    public void indexDocs(final List<String> regexIndex, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            List<String> allFileNames = Files.walk(path).filter(Files::isRegularFile)
                    .map(x -> x.getFileName().toString()).collect(Collectors.toList());
            allFileNames.forEach(file -> logger.debug(file));
            regexIndex.addAll(allFileNames);
        }
    }

    public List<String> searchInIndex(List<String> index, Pattern pattern) {
        List<String> findings = new LinkedList<>();
        for(String fileName : index) {
            Matcher matcher = pattern.matcher(fileName);
            if (matcher.find()){
                findings.add(fileName);
            }
        }

        return findings;
    }

    public List<String> searchInPartition(String regex, int partition) {
        Pattern pattern = Pattern.compile(regex);
        return searchInIndex(regexIndexes.get(partition), pattern);
    }

}
