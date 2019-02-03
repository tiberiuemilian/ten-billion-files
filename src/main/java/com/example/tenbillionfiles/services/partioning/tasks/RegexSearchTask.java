package com.example.tenbillionfiles.services.partioning.tasks;

import com.example.tenbillionfiles.services.RegexIndexService;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class RegexSearchTask implements PartitionTask<List<String>, String> {

    @Getter @Setter
    private RegexIndexService regexIndexService;

    @Getter @Setter
    private String taskName;

    @Getter @Setter
    private int partition;

    public RegexSearchTask() {
    }

    public RegexSearchTask(RegexIndexService regexIndexService) {
        this.regexIndexService = regexIndexService;
    }

    public RegexSearchTask(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public List<String> process(String regex, CountDownLatch latch) {
        return regexIndexService.searchInPartition(regex, getPartition());
    }

    @Override
    public RegexSearchTask clone() throws CloneNotSupportedException {
        RegexSearchTask newOne = new RegexSearchTask(taskName);
        newOne.setPartition(partition);
        newOne.setRegexIndexService(regexIndexService);
        return newOne;
    }

    @Override
    public String toString() {
        return "RegexSearchTask{" +
                "taskName='" + taskName + '\'' +
                ", partition=" + partition +
                '}';
    }

}
