package com.example.udriBook.service;

import com.example.udriBook.exception.CustomException;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "gcp")
public class GcpStorageServiceImpl implements StorageService {

    private final Storage storage;

    @Value("${gcp.storage.bucket.name}")
    private String bucketName;

    @Override
    public String uploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }

        StringBuilder fileNames = new StringBuilder();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String contentType = file.getContentType();
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.lastIndexOf('.') > 0) {
                extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            }

            boolean isValidType = false;
            if (contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/jpg") || contentType.equals("image/png")
                    || contentType.equals("application/pdf"))) {
                isValidType = true;
            }
            if (!isValidType && (extension.equals("jpg") || extension.equals("jpeg") || extension.equals("png") || extension.equals("pdf"))) {
                isValidType = true;
            }

            if (!isValidType) {
                throw new CustomException("Invalid file type. Only JPEG, JPG, and PDF are allowed.");
            }

            try {
                String fileExtension = extension.isEmpty() ? "" : "." + extension;
                String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

                BlobId blobId = BlobId.of(bucketName, uniqueFileName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build();
                
                storage.create(blobInfo, file.getBytes());

                if (fileNames.length() > 0) {
                    fileNames.append(",");
                }
                fileNames.append(uniqueFileName);

            } catch (IOException ex) {
                log.error("Error uploading to GCP Storage: {}", ex.getMessage());
                throw new CustomException("Could not store file in GCP Storage. Please try again!");
            }
        }
        return fileNames.toString();
    }

    @Override
    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        try {
            BlobId blobId = BlobId.of(bucketName, fileName.trim());
            return storage.delete(blobId);
        } catch (Exception e) {
            log.warn("Could not delete file '{}' from GCP: {}", fileName, e.getMessage());
            return false;
        }
    }

    @Override
    public int[] deleteFiles(String fileNamesStr) {
        if (fileNamesStr == null || fileNamesStr.trim().isEmpty()) {
            return new int[]{0, 0};
        }

        int deleted = 0;
        int missing = 0;

        for (String rawName : fileNamesStr.split(",")) {
            String fileName = rawName.trim();
            if (fileName.isEmpty()) continue;

            if (deleteFile(fileName)) {
                log.info("File deleted from GCP: '{}'", fileName);
                deleted++;
            } else {
                missing++;
            }
        }
        return new int[]{deleted, missing};
    }
}
