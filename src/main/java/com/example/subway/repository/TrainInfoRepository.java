// TrainInfoRepository.java
package com.example.subway.repository;

import com.example.subway.domain.TrainInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainInfoRepository extends JpaRepository<TrainInfo, Long> {
    Optional<TrainInfo> findByTrainNo(String trainNo);

    // 추가: lineNum 기준으로 조회
    List<TrainInfo> findByLineNum(String lineNum);
}
