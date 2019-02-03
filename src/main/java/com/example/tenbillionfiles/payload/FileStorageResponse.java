package com.example.tenbillionfiles.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class FileStorageResponse {
    private String fileName;
    private String fileDownloadUri;
    private String fileType;
    private long size;

}