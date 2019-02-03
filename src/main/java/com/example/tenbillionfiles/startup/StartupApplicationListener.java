package com.example.tenbillionfiles.startup;

import com.example.tenbillionfiles.services.CounterService;
import com.example.tenbillionfiles.services.FileStorageService;
import com.example.tenbillionfiles.services.LuceneIndexService;
import com.example.tenbillionfiles.services.RegexIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class StartupApplicationListener implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private RegexIndexService regexIndexService;

    @Autowired
    private LuceneIndexService luceneIndexService;

    @Autowired
    private CounterService counterService;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        fileStorageService.initStorage();

        regexIndexService.initIndexes();
        luceneIndexService.initIndexes();

        counterService.initFileCounter();
    }

}
