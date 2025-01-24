package com.example.subway.repository;

import com.example.subway.domain.SubwayData;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubwayDataRepository extends JpaRepository<SubwayData, Long> {
    // line별로 가져오기
    // List<SubwayData> findByLinenum(String linenum);
}
