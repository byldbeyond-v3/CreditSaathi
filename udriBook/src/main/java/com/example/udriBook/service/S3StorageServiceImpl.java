package com.example.udriBook.service;

import com.example.udriBook.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.provider", havingValue = "aws")
public class S3StorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket.name}")
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

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(uniqueFileName)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

                if (fileNames.length() > 0) {
                    fileNames.append(",");
                }
                fileNames.append(uniqueFileName);

            } catch (IOException | S3Exception ex) {
                log.error("Error uploading to S3: {}", ex.getMessage());
                throw new CustomException("Could not store file in S3. Please try again!");
            }
        }
        return fileNames.toString();
    }

    @Override
    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName.trim())
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (S3Exception e) {
            log.warn("Could not delete file '{}' from S3: {}", fileName, e.getMessage());
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
                log.info("File deleted from S3: '{}'", fileName);
                deleted++;
            } else {
                missing++;
            }
        }
        return new int[]{deleted, missing};
    }
}
