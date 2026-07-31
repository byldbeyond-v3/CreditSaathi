package com.example.udriBook.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerAttachmentDto {
    private Long dueId;
    private String fileName;
    private String description;
    private LocalDateTime createdAt;
}
