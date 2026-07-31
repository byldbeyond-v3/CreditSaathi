package com.example.udriBook.service;

import com.example.udriBook.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private final Path fileStorageLocation;

    public LocalStorageServiceImpl() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new CustomException("Could not create the directory where the uploaded files will be stored.");
        }
    }

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

                Path targetLocation = this.fileStorageLocation.resolve(uniqueFileName);
                Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

                if (fileNames.length() > 0) {
                    fileNames.append(",");
                }
                fileNames.append(uniqueFileName);

            } catch (IOException ex) {
                throw new CustomException("Could not store file. Please try again!");
            }
        }
        return fileNames.toString();
    }

    @Override
    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        try {
            Path resolved = fileStorageLocation.resolve(fileName).normalize();
            if (!resolved.startsWith(fileStorageLocation)) {
                log.warn("Path traversal blocked — skipping file: '{}'", fileName);
                return false;
            }
            return Files.deleteIfExists(resolved);
        } catch (IOException e) {
            log.warn("Could not delete file '{}': {}", fileName, e.getMessage());
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
                log.info("File deleted from disk: '{}'", fileName);
                deleted++;
            } else {
                log.warn("File not found or failed to delete: '{}'", fileName);
                missing++;
            }
        }
        return new int[]{deleted, missing};
    }
}
