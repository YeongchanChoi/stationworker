package com.example.subway.controller;

import com.example.subway.domain.TrainInfo;
import com.example.subway.dto.StationTrainResponse;
import com.example.subway.repository.TrainInfoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController
public class StationController {

    private final TrainInfoRepository trainInfoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StationController(TrainInfoRepository trainInfoRepository) {
        this.trainInfoRepository = trainInfoRepository;
    }

    @GetMapping("/api/station/{stationName}")
    public List<StationTrainResponse> getTrainsPassingStation(
            @PathVariable("stationName") String stationName) {

        List<TrainInfo> allTrains = trainInfoRepository.findAll();
        List<StationTrainResponse> result = new ArrayList<>();

        for (TrainInfo train : allTrains) {
            // Parse the JSON stations array into a List<String>
            List<String> path = convertJsonToList(train.getStationsJson());
            if (path == null) {
                // Parsing error or empty JSON
                continue;
            }

            // 1) Find the index of the requested station
            int stationIndex = path.indexOf(stationName);
            if (stationIndex == -1) {
                // This train does not pass through stationName
                continue;
            }

            // 2) Find the index of the train’s current station
            int currIndex = path.indexOf(train.getCurrentStation());
            if (currIndex == -1) {
                // Edge case: currentStation not found in stationsJson
                continue;
            }

            // 3) Calculate remaining stations
            int remainingStations = stationIndex - currIndex;
            if (remainingStations < 0) {
                // Already passed the station
                continue;
            }

            // 4) Build the response DTO
            StationTrainResponse dto = new StationTrainResponse(
                    train.getTrainNo(),
                    train.getUpDown(),
                    train.getExpressYn(),
                    train.getCurrentStation(),
                    train.getEndStation(),
                    remainingStations
            );
            result.add(dto);
        }

        // Sort by ascending “remainingStations”
        result.sort(Comparator.comparingInt(StationTrainResponse::getRemainingStations));

        return result;
    }

    /**
     * Helper method to parse a JSON string representing an array of stations
     * into a List<String> using Jackson.
     */
    private List<String> convertJsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.error("Failed to parse JSON string: {}", json, e);
            return null;
        }
    }
}
