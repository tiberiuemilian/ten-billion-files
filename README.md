[![Build Status](https://travis-ci.org/tiberiuemilian/ten-billion-files.svg?branch=master)](https://travis-ci.org/tiberiuemilian/ten-billion-files) 

## Ten billions files project

The upper limit of this storage system raises at least following concerns:

1. The theoretic maximal limit for EXT4 formatted partitions is : 2^32 - 1 < 10 billions<br/>
As an workaround we can format the drive ZFS or other partition types that moves the upper limit to 2^64 or more.
 
2. Regex & lucene searches; file counting times will be very slow because will depend on a single process/thread
for the execution.

3. Asynchronous operation style will be totally discouraged by the atomicity for create/modify/delete file operations.  


A solution for these drawbacks is to divide the problem space through sharding.
We will divide the storage drive in multiple partitions that will support parallel map-reduce operations.

Because the project is a kind of POC having more academic scope than a production one, I used a simple 
partitioning scheme. The file allocation scheme are based on the hash value of the file name string.
The normal distribution is provided through a modulo - {partion} operation on file name's hash number.
For speeding up the calculus for partition we prefer bitwise modulo calculation.
This imposes as the number of partitions to be a power of 2. (in my implementation: 2^4 = 16)

For a production system will be important not only the normal distribution for the number of files but also the 
normal distribution for the storage space used for storing them. A solution in this case could be to split files in chunks
with a statistic determined best dimension and storing and returning files as sum of chunks.

The implementation is based on Spring Boot, and the main class for it is:<br/>
_TenBillionFilesApplication_

Application configurations are placed in _config_ package:</br>
_StorageConfigurations_ which loads _application.properties_ property file</br>
_SwaggerConfig_ which adds a swagger documentation & UI interface to the project

After application starts all REST API end-points could be operated through an web interface located to following URL: [http://localhost:8080/swagger-ui.htm](http://localhost:8080/swagger-ui.htm)

The main controller that exposes the REST API is: _FileController_</br>
This behaves asynchronous similar to a Servlet context using a multithreaded pool executor.

All the backend operations are delegated by the controller to: _FileStorageService_

For completion of the tasks this service uses other services like: _CounterService_, _RegexIndexService_, _LuceneIndexService_, _PartitioningService_

File name searches are exposed in 2 ways:
1. Regex searches that are more flexible in terms of searching pattern but could be slow for large pools of files even when caching the precompiled matching pattern
2. Lucene search that are not so flexible even it permits wildcards searches and file name tokenization, but offers better searching times for large file sets through custom data structure used for storing names in the index     


 