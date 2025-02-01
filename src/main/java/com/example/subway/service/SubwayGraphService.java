package com.example.subway.service;

import com.example.subway.domain.SubwayData;
import com.example.subway.repository.SubwayDataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class SubwayGraphService {

    private final SubwayDataRepository subwayDataRepository;

    // BFS 실패 건을 기록할 JSON 파일 경로
    private static final String FAILED_BFS_FILE = "bfs_failures.json";

    public SubwayGraphService(SubwayDataRepository subwayDataRepository) {
        this.subwayDataRepository = subwayDataRepository;
    }

    /**
     * 특정 호선(lineNum)에 대응하는 인접 그래프를 생성 (일반 노선 기준).
     */
    public Map<String, List<String>> buildGraph(String lineNum) {
        return buildGraph(lineNum, false);
    }

    /**
     * 특정 호선(lineNum)에 대해, expressOnly 플래그에 따라
     * 일반 노선 / 급행 노선만 필터링하여 인접 그래프를 생성.
     */
    public Map<String, List<String>> buildGraph(String lineNum, boolean expressOnly) {
        List<SubwayData> dataList = subwayDataRepository.findAll();

        Map<String, List<String>> graph = new HashMap<>();
        for (SubwayData data : dataList) {
            // 1) 호선 필터
            if (!data.getLinenum().equals(lineNum)) {
                continue;
            }
            // 2) 급행 여부 필터
            if (expressOnly && !"Y".equalsIgnoreCase(data.getExpressYn())) {
                continue;
            }
            if (!expressOnly && "Y".equalsIgnoreCase(data.getExpressYn())) {
                continue;
            }

            String front = data.getFrontStationname();
            String back  = data.getBackStationname();

            // 양방향 그래프 구성
            graph.computeIfAbsent(front, k -> new ArrayList<>()).add(back);
            graph.computeIfAbsent(back, k -> new ArrayList<>()).add(front);
        }
        return graph;
    }

    /**
     * BFS로 start에서 end로 가는 경로를 탐색 (호선 정보를 알 수 없을 때 오버로드).
     * 기존 코드를 깨지 않기 위해 남겨둔 메서드입니다.
     */
    public List<String> bfsPath(Map<String, List<String>> graph, String start, String end) {
        // 호선 정보를 알 수 없으면 임시로 "UNKNOWN_LINE" 사용
        return bfsPath(graph, start, end, "UNKNOWN_LINE");
    }

    /**
     * BFS로 start에서 end로 가는 경로를 탐색 (호선(lineNum) 포함).
     * 실패 시 "왜 못찾았는지(reason)" + "어디까지 탐색했는지(visited)" + "호선(lineNum)"을
     * JSON 파일(Pretty Print)로 기록합니다.
     */
    public List<String> bfsPath(Map<String, List<String>> graph, String start, String end, String lineNum) {
        log.info("----- BFS START [lineNum={}] from '{}' to '{}' -----", lineNum, start, end);

        // (1) start/end 노드가 그래프에 없는 경우 -> 실패 기록
        if (!graph.containsKey(start) || !graph.containsKey(end)) {
            log.warn("[BFS] start='{}' 혹은 end='{}'가 lineNum='{}' 그래프에 없습니다.", start, end, lineNum);
            logFailedBfs(start, end, lineNum,
                    "Start 혹은 End 노드가 그래프에 존재하지 않음",
                    Collections.emptySet());
            return Collections.emptyList();
        }

        // (2) BFS 준비
        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        visited.add(start);
        parent.put(start, null);
        queue.offer(start);

        // (3) BFS 탐색
        while (!queue.isEmpty()) {
            String current = queue.poll();
            log.debug("[BFS] 현재 방문 노드: {}", current);

            if (current.equals(end)) {
                // 목적지 도착 -> 성공
                log.debug("[BFS] 목적지 '{}'에 도달했습니다.", end);
                break;
            }

            List<String> neighbors = graph.get(current);
            if (neighbors == null) {
                continue;
            }
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.offer(neighbor);
                    log.debug("[BFS] 새로운 이웃 '{}' 발견 -> queue에 추가", neighbor);
                }
            }
        }

        // (4) BFS 종료 후, end 노드를 찾지 못했으면 실패 기록
        if (!parent.containsKey(end)) {
            log.warn("[BFS] '{}'에서 '{}'로 가는 경로를 찾지 못했습니다. (lineNum={})", start, end, lineNum);
            logFailedBfs(start, end, lineNum,
                    "BFS 종료 후 end 노드를 찾지 못함",
                    visited);
            return Collections.emptyList();
        }

        // (5) 경로 역추적
        List<String> path = new ArrayList<>();
        String node = end;
        while (node != null) {
            path.add(node);
            node = parent.get(node);
        }
        Collections.reverse(path);

        log.info("[BFS] '{}' -> '{}' (lineNum={}) 경로: {}", start, end, lineNum, path);
        return path;
    }

    /**
     * BFS 실패 시, JSON 파일(bfs_failures.json)에 기록하는 메서드.
     * - "start", "end", "lineNum", "reason", "visited", "timestamp" 등을 함께 기록
     */
    private void logFailedBfs(String start,
                              String end,
                              String lineNum,
                              String reason,
                              Set<String> visited) {
        // ObjectMapper에 Pretty Print(들여쓰기) 설정
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        List<Map<String, Object>> failedList = new ArrayList<>();
        File file = new File(FAILED_BFS_FILE);

        // 이미 파일이 있으면 기존 내용을 불러옴
        if (file.exists()) {
            try {
                failedList = mapper.readValue(file, new TypeReference<>() {});
            } catch (IOException e) {
                log.error("Error reading {}: {}", FAILED_BFS_FILE, e.getMessage());
            }
        }

        // 새로운 실패 기록 생성
        Map<String, Object> record = new HashMap<>();
        record.put("start", start);
        record.put("end", end);
        record.put("lineNum", lineNum);  // 호선 정보
        record.put("timestamp", LocalDateTime.now().toString());
        record.put("reason", reason);    // BFS 실패 사유
        record.put("visited", new ArrayList<>(visited));  // 방문했던 노드 목록
        record.put("message", "BFS 경로를 찾지 못했습니다.");

        failedList.add(record);

        // JSON 파일에 다시 기록
        try {
            mapper.writeValue(file, failedList);
            log.info("BFS 실패 기록을 {} 파일에 저장했습니다.", FAILED_BFS_FILE);
        } catch (IOException e) {
            log.error("Error writing to {}: {}", FAILED_BFS_FILE, e.getMessage());
        }
    }

}
