package com.example.udriBook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFilesDeletionResultDto {

    private Long customerId;
    private int  filesDeletedFromDisk;
    private int  filesMissingOnDisk;
    private int  dueRecordsCleared;
    private int  transactionRecordsCleared;
}
