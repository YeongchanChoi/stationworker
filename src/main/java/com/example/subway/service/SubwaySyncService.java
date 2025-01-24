package com.example.subway.service;

import com.example.subway.domain.TrainInfo;
import com.example.subway.repository.TrainInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class SubwaySyncService {

    private final TrainInfoRepository trainInfoRepository;
    private final SubwayGraphService subwayGraphService;

    // 예시 API 키 (테스트용)
    private static final String API_KEY = "755455636167793236306850475a51";
    // swopenAPI: (API_KEY, 노선명) 순서로 URL 구성
    private static final String SUBWAY_API_URL
            = "http://swopenAPI.seoul.go.kr/api/subway/%s/xml/realtimePosition/0/100/%s";

    public SubwaySyncService(TrainInfoRepository trainInfoRepository,
                             SubwayGraphService subwayGraphService) {
        this.trainInfoRepository = trainInfoRepository;
        this.subwayGraphService  = subwayGraphService;
    }

    /**
     * 지정된 주기로 1~2호선 열차정보를 동기화 (테스트 예시)
     * 실제로는 1~9호선을 순회하도록 수정 가능
     */
    @Scheduled(fixedDelayString = "${myapp.schedule.subway-refresh}")
    public void syncTrains() {
        for (int line = 1; line <= 9; line++) {
            String lineNum = line + "호선";
            try {
                // URL 인코딩
                String lineNameEncoded = URLEncoder.encode(lineNum, "UTF-8");
                syncLine(lineNameEncoded, lineNum);
            } catch (Exception e) {
                log.error("Failed to sync line {}: {}", lineNum, e.getMessage(), e);
            }   
        }
    }

    /**
     * 특정 호선(lineNameEncoded, lineNum)에 대한 열차정보 동기화 + DB 정리 로직
     */
    private void syncLine(String lineNameEncoded, String lineNum) throws Exception {
        // 1) API 호출
        String apiUrl = String.format(SUBWAY_API_URL, API_KEY, lineNameEncoded);
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new URL(apiUrl).openStream());
        doc.getDocumentElement().normalize();

        NodeList rowList = doc.getElementsByTagName("row");
        if (rowList.getLength() == 0) {
            log.info("[INFO] {}: 열차 정보가 없습니다.", lineNum);
            // 혹시 필요하다면, 여기서 해당 호선의 모든 열차 삭제 로직을 추가해도 됨.
            return;
        }

        // 2) 이 노선(lineNum)에 대한 그래프 구성 (BFS 용)
        Map<String, List<String>> graph = subwayGraphService.buildGraph(lineNum);

        // 3) 이번 동기화에서 발견된 trainNo를 추적하기 위한 Set
        Set<String> fetchedTrainNos = new HashSet<>();

        // 4) API 응답(rowList) 순회 후, DB Insert/Update
        for (int i = 0; i < rowList.getLength(); i++) {
            Node row = rowList.item(i);
            if (row.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) row;

            // 열차번호
            String trainNo  = getTagValue(element, "trainNo");
            // 상/하행
            String upDown   = getTagValue(element, "updnLine");
            // 막차 여부 (예시)
            String expressYn= getTagValue(element, "expressyn");
            // 현재역
            String currStatn= getTagValue(element, "statnNm");
            // 종착역
            String endStatn = getTagValue(element, "statnTnm");

            // 필수 정보 누락 시 스킵
            if (trainNo == null || currStatn == null || endStatn == null) {
                log.warn("Skipping row due to null values: trainNo={}, currStatn={}, endStatn={}",
                        trainNo, currStatn, endStatn);
                continue;
            }

            // 새로 발견된 trainNo 기록
            fetchedTrainNos.add(trainNo);

            // DB에서 trainNo 존재 여부 확인
            Optional<TrainInfo> optTrain = trainInfoRepository.findByTrainNo(trainNo);
            TrainInfo train;
            if (optTrain.isEmpty()) {
                // --- [새 열차] ---
                train = new TrainInfo();
                train.setTrainNo(trainNo);
                train.setUpDown(upDown);
                train.setExpressYn(expressYn);
                train.setCurrentStation(currStatn);
                train.setEndStation(endStatn);
                // [추가] 호선 저장
                train.setLineNum(lineNum);

                // BFS로 현재역~종착역 사이 경로 계산
                List<String> path = subwayGraphService.bfsPath(graph, currStatn, endStatn);
                log.debug("[NEW TRAIN] BFS path: {}", path);

                // path를 JSON 문자열로 변환해서 저장
                train.setStationsJson(toJsonString(path));
                train.setUpdateTime(LocalDateTime.now());
                trainInfoRepository.save(train);

                log.info("[NEW TRAIN] {} - {} ( {} -> {} )", trainNo, lineNum, currStatn, endStatn);

            } else {
                // --- [기존 열차 업데이트] ---
                train = optTrain.get();
                train.setUpDown(upDown);
                train.setExpressYn(expressYn);
                train.setCurrentStation(currStatn);
                train.setEndStation(endStatn);
                train.setLineNum(lineNum);

                // 기존에 저장된 JSON 경로에서 "이미 지난 역"을 제거하는 로직
                List<String> path = fromJsonString(train.getStationsJson());
                if (path != null && !path.isEmpty()) {
                    int currIdx = path.indexOf(currStatn);
                    // 현재역보다 앞에 있는 역들은 지난 것으로 보고 제거
                    if (currIdx > 0) {
                        for (int idx = 0; idx < currIdx; idx++) {
                            path.set(idx, null);
                        }
                        path.removeIf(Objects::isNull);
                    }
                    train.setStationsJson(toJsonString(path));
                }

                train.setUpdateTime(LocalDateTime.now());
                trainInfoRepository.save(train);

                log.info("[UPDATE TRAIN] {} - {} (현재역: {})", trainNo, lineNum, currStatn);
            }
        }

        // 5) === [사라진 열차] 정리 로직 ===
        //    이번 동기화에서 발견되지 않은(= fetchedTrainNos에 없는) 열차를 DB에서 삭제
        List<TrainInfo> existingTrains = trainInfoRepository.findByLineNum(lineNum);
        for (TrainInfo t : existingTrains) {
            // 해당 lineNum에 속하지만, 이번 동기화에서 발견되지 않은 trainNo
            if (!fetchedTrainNos.contains(t.getTrainNo())) {
                // 운행 종료된 것으로 판단하여 DB에서 제거
                trainInfoRepository.delete(t);
                log.info("[DELETE TRAIN] {} - {} (운행 종료)", t.getTrainNo(), lineNum);
            }
        }
    }

    /**
     * XML Element에서 특정 tag 텍스트 값을 추출하는 헬퍼 메서드
     */
    private String getTagValue(Element element, String tag) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent();
    }

    /**
     * List<String> -> JSON 문자열 변환 (간단 버전)
     * 예: ["서울역","시청역","종각역"]
     */
    private String toJsonString(List<String> list) {
        if (list == null) return "[]";
        return "[" + String.join(",",
                list.stream().map(s -> "\"" + s + "\"").toList()
        ) + "]";
    }

    /**
     * JSON 문자열 -> List<String> 변환 (간단 버전)
     */
    private List<String> fromJsonString(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            if (trimmed.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> result = new ArrayList<>();
            String[] tokens = trimmed.split(",");
            for (String t : tokens) {
                String val = t.trim().replaceAll("\"", "");
                result.add(val);
            }
            return result;
        }
        return new ArrayList<>();
    }
}
