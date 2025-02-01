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
    public List<StationTrainResponse> getTrainsPassingStation(@PathVariable("stationName") String stationName) {
        List<TrainInfo> allTrains = trainInfoRepository.findAll();
        List<StationTrainResponse> result = new ArrayList<>();

        for (TrainInfo train : allTrains) {
            // JSON 형식의 역 목록을 List<String>으로 파싱
            List<String> path = convertJsonToList(train.getStationsJson());
            if (path == null) {
                continue;
            }

            // 요청받은 stationName의 인덱스 확인
            int stationIndex = path.indexOf(stationName);
            if (stationIndex == -1) {
                continue;
            }

            // 현재역의 인덱스 확인
            int currIndex = path.indexOf(train.getCurrentStation());
            if (currIndex == -1) {
                continue;
            }

            int remainingStations = stationIndex - currIndex;
            if (remainingStations < 0) {
                continue;
            }

            // lstcarAt을 boolean으로 변환 ("1"이면 true, 그 외에는 false)
            boolean isLastTrain = "1".equals(train.getLstcarAt());

            StationTrainResponse dto = new StationTrainResponse(
                    train.getTrainNo(),
                    train.getUpDown(),
                    isLastTrain,
                    train.getCurrentStation(),
                    train.getEndStation(),
                    remainingStations,
                    train.getLineNum()
            );
            result.add(dto);
        }

        // 남은 정거장 수 기준 오름차순 정렬
        result.sort(Comparator.comparingInt(StationTrainResponse::getRemainingStations));
        return result;
    }

    /**
     * JSON 문자열을 List<String>으로 변환하는 헬퍼 메서드
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
