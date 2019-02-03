package com.example.tenbillionfiles.services;

import com.example.tenbillionfiles.exception.FileStorageException;
import com.example.tenbillionfiles.services.partioning.PartitioningService;
import com.example.tenbillionfiles.services.partioning.results.SearchResults;
import com.example.tenbillionfiles.services.partioning.tasks.LuceneTask;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.example.tenbillionfiles.config.StorageConfigurations.PARTITIONS_NUMBER;

@Service
public class LuceneIndexService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexService.class);

    private static final String INDEXED_FIELD = "fileName";

    private static final String ID = "id";

    private static final int MAX_HITS = 10;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private PartitioningService partitioningService;

    @Autowired
    private RegexIndexService regexIndexService;

    @Autowired
    private LuceneIndexService luceneIndexService;

    public void initIndexes() {

        try {
            Date start = new Date();
            logger.info("Start lucene-indexes initialization.");
            for (int partition = 0; partition< PARTITIONS_NUMBER; partition++) {
                logger.debug("Indexing partition {}", partition);
                Path partitionPath = fileStorageService.getStorageLocation(partition);
                logger.debug("Partition path for partition {} is: '{}'", partition, partitionPath.toAbsolutePath());
                if (!Files.isReadable(partitionPath)) {
                    logger.error("Document directory '" + partitionPath.toAbsolutePath()+ "' does not exist or is not readable, please check the path");
                    System.exit(1);
                }
                Path indexPath = fileStorageService.getIndexLocation(partition);
                logger.debug("Indexing to directory '" + indexPath + "'...");
                Directory dir = FSDirectory.open(indexPath);
                Analyzer analyzer = new StandardAnalyzer();
                IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

                // Optional: for better indexing performance, if you
                // are indexing many documents, increase the RAM
                // buffer.  But if you do this, increase the max heap
                // size to the JVM (eg add -Xmx512m or -Xmx1g):
                //
                // iwc.setRAMBufferSizeMB(256.0);

                IndexWriter writer = new IndexWriter(dir, iwc);

                indexDocs(writer, partitionPath);

                writer.close();
            }
            logger.info("End lucene-indexes initialization.");
            Date end = new Date();
            logger.debug(end.getTime() - start.getTime() + " total milliseconds");
        } catch (IOException e) {
            throw new FileStorageException("Could not create lucene indexes.", e);
        }
    }

    public void indexDocs(final IndexWriter writer, Path docDir) throws IOException {
        if (Files.isDirectory(docDir)) {
            Stream<Path> walk = Files.walk(docDir);
            List<String> allFileNames = walk.filter(Files::isRegularFile)
                        .map(x -> x.getFileName().toString()).collect(Collectors.toList());

            for (String fileName : allFileNames) {
                indexDoc(writer, fileName);
            }
        }
    }

    public void indexDoc(IndexWriter writer, String fileName) throws IOException {
        Document doc = new Document();
        Field idField = new StringField(ID, fileName, Field.Store.YES);
        Field fileNameField = new TextField(INDEXED_FIELD, fileName, Field.Store.YES);
        doc.add(idField);
        doc.add(fileNameField);

        if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            logger.debug("adding " + fileName);
            writer.addDocument(doc);
        } else {
            // Existing index (an old copy of this document may have been indexed) so
            // we use updateDocument instead to replace the old one matching the exact
            // path, if present:
            logger.debug("updating " + fileName);
            writer.updateDocument(new Term("fileName", fileName), doc);
        }
    }

    public void indexDoc(String fileName, IndexWriterConfig.OpenMode mode) throws IOException {
        Path indexPath = fileStorageService.getIndexLocation(partitioningService.getPartition(fileName));
        logger.debug("Indexing to directory '" + indexPath + "'...");
        Directory dir = FSDirectory.open(indexPath);
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(mode);
        IndexWriter writer = new IndexWriter(dir, iwc);
        indexDoc(writer, fileName);
        writer.close();
    }

    public void removeDocFromIndex(String fileName) throws IOException {
        Path indexPath = fileStorageService.getIndexLocation(partitioningService.getPartition(fileName));
        Directory dir = FSDirectory.open(indexPath);
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(dir, iwc);

        Term term = new Term(ID, fileName);
        writer.deleteDocuments(term);
        writer.flush();
        writer.close();
    }

    /**
     * single threaded
     */
    public List<String> search(String query) throws IOException, ParseException {
        List<String> findings = new LinkedList<>();
        for (int partition = 0; partition< PARTITIONS_NUMBER; partition++) {
            findings.addAll(searchInPartition(query, partition));
        }
        return findings;
    }

    /**
     * multithreaded search
     * @param query
     * @return
     * @throws CloneNotSupportedException
     */
    public List<String> multiThreadSearch(String query) throws CloneNotSupportedException {
        SearchResults searchResults = new SearchResults();
        partitioningService.runOnAllPartitions(query, new LuceneTask(this), searchResults, 60, TimeUnit.SECONDS);
        return searchResults.getResults();
    }

    public List<String> searchInPartition(String queryString, int partition) throws IOException, ParseException {
        Path indexPath = fileStorageService.getIndexLocation(partition);
        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer();
        QueryParser parser = new QueryParser(INDEXED_FIELD, analyzer);

        // with performance drawback
        parser.setAllowLeadingWildcard(true);
        Query query = parser.parse(queryString.trim());

        TopDocs searchResults = searcher.search(query, MAX_HITS);
        ScoreDoc[] hits = searchResults.scoreDocs;
        int start = 0;
        int end = Math.min(hits.length, MAX_HITS);
        List<String> resultList = new ArrayList<>(end);
        for (int i = start; i < end; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String fileName = doc.get(INDEXED_FIELD);
            resultList.add(fileName);
        }
        return resultList;
    }

}
