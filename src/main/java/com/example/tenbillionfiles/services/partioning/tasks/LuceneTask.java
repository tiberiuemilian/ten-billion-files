package com.example.tenbillionfiles.services.partioning.tasks;

import com.example.tenbillionfiles.exception.FileStorageException;
import com.example.tenbillionfiles.services.LuceneIndexService;
import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.queryparser.classic.ParseException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class LuceneTask implements PartitionTask<List<String>, String> {

    @Getter @Setter
    private LuceneIndexService luceneIndexService;

    @Getter @Setter
    private String taskName;

    @Getter @Setter
    private int partition;

    public LuceneTask() {
    }

    public LuceneTask(LuceneIndexService luceneIndexService) {
        this.luceneIndexService = luceneIndexService;
    }

    public LuceneTask(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public List<String> process(String query, CountDownLatch latch) {
        List<String> result = null;
        try {
            result = luceneIndexService.searchInPartition(query, getPartition());
        } catch (IOException|ParseException e) {
            throw new FileStorageException("Exception searching '" + query + "' in partition [" + getPartition() + "] .", e);
        }
        return result;
    }

    @Override
    public LuceneTask clone() throws CloneNotSupportedException {
        LuceneTask newOne = new LuceneTask(taskName);
        newOne.setPartition(partition);
        newOne.setLuceneIndexService(luceneIndexService);
        return newOne;
    }

    @Override
    public String toString() {
        return "LuceneTask{" +
                "taskName='" + taskName + '\'' +
                ", partition=" + partition +
                '}';
    }
}
