package com.example.udriBook.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface StorageService {
    /**
     * Uploads a list of files to the storage provider.
     * @param files List of files to upload.
     * @return A comma-separated string of file names or URLs.
     */
    String uploadFiles(List<MultipartFile> files);

    /**
     * Deletes a specific file from the storage provider.
     * @param fileName The name of the file to delete.
     * @return true if deleted successfully, false otherwise.
     */
    boolean deleteFile(String fileName);

    /**
     * Deletes multiple files by parsing a comma-separated string.
     * @param fileNames Comma-separated list of file names to delete.
     * @return An array of counts [deleted, missing].
     */
    int[] deleteFiles(String fileNames);
}
