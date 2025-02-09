package com.example.subway.repository;

import com.example.subway.domain.SubwayStations;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubwayStationRepository extends JpaRepository<SubwayStations, String> {
    // stationcode(String) 이 PK

    // 기존의 findByStationname 메서드 대신,
    // 해당 역이 존재하는지만 확인하는 메서드를 추가합니다.
    boolean existsByStationname(String stationname);
}
