package com.example.udriBook.service;

import com.example.udriBook.dto.FeedbackDto;
import com.example.udriBook.entity.Feedback;
import com.example.udriBook.entity.UserEntity;
import com.example.udriBook.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public Feedback saveFeedback(FeedbackDto feedbackDto, UserEntity user) {
        log.info("Saving feedback for user: {} (ID: {})", user.getOwnerName(), user.getId());
        
        Feedback feedback = Feedback.builder()
                .user(user)
                .userName(user.getOwnerName())
                .rating(feedbackDto.getRating())
                .comments(feedbackDto.getComments())
                .build();
        
        return feedbackRepository.save(feedback);
    }
}
