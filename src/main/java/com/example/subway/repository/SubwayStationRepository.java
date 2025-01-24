package com.example.subway.repository;

import com.example.subway.domain.SubwayStations;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubwayStationRepository extends JpaRepository<SubwayStations, String> {
    // stationcode(String) 이 PK
}
