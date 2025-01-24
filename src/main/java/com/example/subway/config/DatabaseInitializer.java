package com.example.subway.config;

import com.example.subway.repository.TrainInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * 애플리케이션이 시작될 때 train_info 테이블을 초기화(전량 삭제)하는 예시
 */
@Slf4j
@Component
public class DatabaseInitializer {

    private final TrainInfoRepository trainInfoRepository;

    public DatabaseInitializer(TrainInfoRepository trainInfoRepository) {
        this.trainInfoRepository = trainInfoRepository;
    }

    @PostConstruct
    public void initDatabase() {
        // (1) 기존 train_info 내용 모두 삭제
        trainInfoRepository.deleteAll();

        // (2) 필요한 경우, 초기 데이터 Insert 가능
        // ex) trainInfoRepository.save(...);

        log.info("[INIT] train_info 테이블을 초기화(deleteAll)했습니다.");
    }
}
