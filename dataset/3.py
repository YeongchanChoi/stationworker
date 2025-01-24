import pymysql
from collections import defaultdict

DB_HOST = 'localhost'
DB_USER = 'root'
DB_PASSWORD = '1234'
DB_NAME = 'test'

def get_db_connection():
    return pymysql.connect(
        host=DB_HOST,
        user=DB_USER,
        password=DB_PASSWORD,
        db=DB_NAME,
        charset='utf8mb4'
    )

def create_tables():
    """
    SubwayRoute, SubwayRouteStation 테이블이 없으면 생성.
    여기서 SubwayRoute에 direction(INT) 컬럼을 둡니다.
      - 0 = 상행
      - 1 = 하행
      - 9 = 단선(필요 시)
    """
    create_route_table = """
    CREATE TABLE IF NOT EXISTS SubwayRoute (
        id INT AUTO_INCREMENT PRIMARY KEY,
        lineNum VARCHAR(100),
        direction INT,
        branchNo INT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    """

    create_route_station_table = """
    CREATE TABLE IF NOT EXISTS SubwayRouteStation (
        id INT AUTO_INCREMENT PRIMARY KEY,
        routeId INT,
        seq INT,
        outercode VARCHAR(50),
        FOREIGN KEY (routeId) REFERENCES SubwayRoute(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    """

    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(create_route_table)
            cursor.execute(create_route_station_table)
        conn.commit()
    finally:
        conn.close()

def load_all_data():
    """
    subway_data 테이블에서 (linenum, front_outercode, back_outercode)를 전부 읽어온다.
    """
    conn = get_db_connection()
    rows = []
    try:
        with conn.cursor() as cursor:
            sql = """
            SELECT linenum, front_outercode, back_outercode
            FROM subway_data
            """
            cursor.execute(sql)
            rows = cursor.fetchall()
    finally:
        conn.close()

    return rows

# 실제로는 stationname, outercode 등의 정확한 정보를 통해
# 해당 구간이 성수지선/신정지선/2호선본선(내/외선)/6호선응암순환인지 판별해야 합니다.
# 여기서는 간단히 False/True 리턴용 함수를 예시로 두었습니다.

def is_seongsu_jiseon(front, back):
    # 2호선 성수지선 구간인지 판별
    return False

def is_sinjeong_jiseon(front, back):
    # 2호선 신정지선 구간인지 판별
    return False

def is_2ho_main_loop(front, back):
    # 2호선 본선(순환구간)인지 판별
    return True  # 예: 기본값으로 True. 실제론 위 함수들이 False면 이게 True

def is_eungam_loop(front, back):
    # 6호선 응암순환(응암, 역촌, 불광, 독바위, 연신내, 구산, 응암) 구간 판별
    return False

def separate_lines_and_directions(rows):
    """
    (linenum, front, back) -> ((lineNum, direction), [(f, b), ...]) 구조로 분류.
    direction: 0=상행, 1=하행, 9=단선 등

    규칙:
      1) 2호선
         - 성수지선 / 신정지선: direction=9 (단선) 등으로 저장
         - 본선(순환): 내선=상행(0), 외선=하행(1)
      2) 6호선
         - 응암순환 구간은 하행(1)만 저장 (상행=0은 제외)
         - 나머지는 상행(0), 하행(1) 둘 다
      3) 기타 호선
         - (front->back)=하행(1), (back->front)=상행(0)
    """
    # 자료구조 예: { (lineNum, 0): [(f,b), ...], (lineNum, 1): [...], ... }
    line_dir_to_edges = defaultdict(list)

    for (origin_line, front, back) in rows:

        if origin_line == "2호선":
            # === 2호선 분기 ===
            if is_seongsu_jiseon(front, back):
                # 성수지선
                line_dir_to_edges[("2호선(성수지선)", 9)].append((front, back))
            elif is_sinjeong_jiseon(front, back):
                # 신정지선
                line_dir_to_edges[("2호선(신정지선)", 9)].append((front, back))
            else:
                # 2호선 본선(순환구간)
                if is_2ho_main_loop(front, back):
                    # 내선=상행(0): (front->back)
                    line_dir_to_edges[("2호선", 0)].append((front, back))
                    # 외선=하행(1): (back->front)
                    line_dir_to_edges[("2호선", 1)].append((back, front))

        elif origin_line == "6호선":
            # === 6호선 ===
            if is_eungam_loop(front, back):
                # 응암순환 구간은 하행(1)만 저장
                line_dir_to_edges[("6호선", 1)].append((front, back))
                # 상행(0)은 저장 안함
            else:
                # 일반 구간: 상행(0), 하행(1) 둘 다
                # front->back = 하행(1)
                line_dir_to_edges[("6호선", 1)].append((front, back))
                # back->front = 상행(0)
                line_dir_to_edges[("6호선", 0)].append((back, front))

        else:
            # === 기타 호선 ===
            # 상행(0), 하행(1) 분리
            line_dir_to_edges[(origin_line, 1)].append((front, back))  # 하행
            line_dir_to_edges[(origin_line, 0)].append((back, front))  # 상행

    return line_dir_to_edges

def build_adjacency(line_dir_to_edges):
    """
    line_dir_to_edges를 인접 리스트로 변환
    {
      (lineNum, direction): { front_outercode: [back_outercode, ...], ... },
      ...
    }
    """
    adjacency = defaultdict(lambda: defaultdict(list))
    for (lineNum, direction), edges in line_dir_to_edges.items():
        for (f, b) in edges:
            adjacency[(lineNum, direction)][f].append(b)
    return adjacency

def find_all_routes_for_line(line_adj):
    """
    line_adj: { front_outercode: [back_outercode1, ...], ... }
    DFS로 모든 경로(분기 포함) 탐색.
    순환 방지를 위해 visited 사용(필요 시).
    """
    in_degree = defaultdict(int)
    out_degree = defaultdict(int)

    for front, backs in line_adj.items():
        out_degree[front] += len(backs)
        for back in backs:
            in_degree[back] += 1

    all_nodes = set(line_adj.keys())
    for backs in line_adj.values():
        all_nodes.update(backs)

    # 시작역(앞이 없는 역)
    start_nodes = [n for n in all_nodes if in_degree[n] == 0]

    all_paths = []

    def dfs(current_path, visited):
        current_node = current_path[-1]
        if current_node not in line_adj or len(line_adj[current_node]) == 0:
            all_paths.append(current_path[:])
            return
        for next_node in line_adj[current_node]:
            if next_node in visited:
                continue
            current_path.append(next_node)
            visited.add(next_node)
            dfs(current_path, visited)
            visited.remove(next_node)
            current_path.pop()

    # 완전 순환선 등으로 start_nodes가 없으면 임의 노드 하나를 시작점으로 잡음
    if not start_nodes and all_nodes:
        start_nodes = [list(all_nodes)[0]]

    for start_node in start_nodes:
        dfs([start_node], set([start_node]))

    return all_paths

def insert_routes_into_db(lineNum, direction, routes):
    """
    (lineNum, direction)과 해당 routes를 SubwayRoute / SubwayRouteStation에 저장.
    direction은 0=상행, 1=하행, 9=단선(혹은 기타) 등으로 전달됨.
    """
    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            branchNo = 1
            for route in routes:
                insert_route_sql = """
                INSERT INTO SubwayRoute (lineNum, direction, branchNo)
                VALUES (%s, %s, %s)
                """
                cursor.execute(insert_route_sql, (lineNum, direction, branchNo))
                route_id = cursor.lastrowid

                seq = 1
                for outercode in route:
                    insert_station_sql = """
                    INSERT INTO SubwayRouteStation (routeId, seq, outercode)
                    VALUES (%s, %s, %s)
                    """
                    cursor.execute(insert_station_sql, (route_id, seq, outercode))
                    seq += 1

                branchNo += 1

        conn.commit()
    finally:
        conn.close()

def main():
    # 1) 테이블 생성
    create_tables()

    # 2) subway_data에서 모든 데이터 로드
    rows = load_all_data()

    # 3) (lineNum, direction)별 edge 분류
    #    - 2호선 본선: 내선=상행(0), 외선=하행(1)
    #    - 6호선 응암순환: 하행(1)만
    #    - 기타 호선: 상행(0), 하행(1)
    line_dir_to_edges = separate_lines_and_directions(rows)

    # 4) 인접 리스트 구성
    adjacency = build_adjacency(line_dir_to_edges)

    # 5) 각 (lineNum, direction)에 대해 모든 경로 찾고 DB 저장
    for (lineNum, direction), line_adj in adjacency.items():
        routes = find_all_routes_for_line(line_adj)
        if routes:
            insert_routes_into_db(lineNum, direction, routes)

if __name__ == "__main__":
    main()
