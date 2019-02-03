package com.example.tenbillionfiles.controller;

import com.example.tenbillionfiles.payload.FileStorageResponse;
import com.example.tenbillionfiles.services.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileStorageService fileStorageService;

    @PostMapping("/file")
    public FileStorageResponse createFile(@RequestParam("file") MultipartFile file) {
        String fileName = fileStorageService.addFile(file);

        return new FileStorageResponse(fileName, fileStorageService.getDownloadUri(fileName),
                file.getContentType(), file.getSize());

    }

    @PostMapping("/files")
    public List<FileStorageResponse> createMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        return Arrays.asList(files)
                .stream()
                .map(file -> createFile(file))
                .collect(Collectors.toList());
    }

    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> readFile(@PathVariable String fileName, HttpServletRequest request) {
        // Load file as Resource
        Resource resource = fileStorageService.loadFileAsResource(fileName);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            logger.info("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if(contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @PutMapping("/file")
    public FileStorageResponse updateFile(@RequestParam("file") MultipartFile file) {
        String fileName = fileStorageService.modifyFile(file);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/file/")
                .path(fileName)
                .toUriString();

        return new FileStorageResponse(fileName, fileDownloadUri,
                file.getContentType(), file.getSize());
    }

    @DeleteMapping("/file/{fileName}")
    public ResponseEntity deleteFile(@PathVariable String fileName) {
        fileStorageService.deleteFile(fileName);

        return new ResponseEntity(HttpStatus.OK);
    }

    @GetMapping("/search")
    @ResponseBody
    public List<String> search(@RequestParam String query) {
        return fileStorageService.luceneSearch(query);
    }

    @GetMapping("/regex")
    @ResponseBody
    public List<String> regex(@RequestParam String regex) {
        return fileStorageService.regexSearch(regex);
    }

    @GetMapping("/count")
    @ResponseBody
    public long count() {
        return fileStorageService.count();
    }

}
