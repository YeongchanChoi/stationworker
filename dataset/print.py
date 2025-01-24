import pymysql

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

def print_all_subway_routes():
    """
    SubwayRoute + SubwayRouteStation + subway_stations 테이블을 이용해
    호선(lineNum), 방향(direction), 분기(branchNo)별 역(outercode, stationname) 목록을 콘솔 출력.
    """

    conn = get_db_connection()
    try:
        with conn.cursor() as cursor:
            # 1) SubwayRoute 정보를 lineNum, direction, branchNo 순으로 조회
            select_routes_sql = """
            SELECT id, lineNum, direction, branchNo
            FROM SubwayRoute
            ORDER BY lineNum, direction, branchNo
            """
            cursor.execute(select_routes_sql)
            routes = cursor.fetchall()  # [(routeId, lineNum, direction, branchNo), ...]

            for route_info in routes:
                route_id, line_num, direction, branch_no = route_info

                # direction 값(0=상행, 1=하행, 9=단선 등)을 문자열로 변환
                if direction == 0:
                    dir_str = "상행(0)"
                elif direction == 1:
                    dir_str = "하행(1)"
                elif direction == 9:
                    dir_str = "단선(9)"
                else:
                    dir_str = f"기타({direction})"

                # 2) 해당 route_id에 대한 역 목록을 stationname까지 JOIN하여 seq 순으로 조회
                select_stations_sql = """
                SELECT srs.outercode, st.stationname
                FROM SubwayRouteStation srs
                JOIN subway_stations st ON srs.outercode = st.outercode
                WHERE srs.routeId = %s
                ORDER BY srs.seq
                """
                cursor.execute(select_stations_sql, (route_id,))
                station_rows = cursor.fetchall()  # [(outercode, stationname), ...]

                # 3) "outercode(역이름)" 형태로 리스트 생성
                station_list = [f"{row[0]}({row[1]})" for row in station_rows]

                # 4) 출력
                print(f"=== [호선: {line_num}, 방향: {dir_str}, 분기: {branch_no}] ===")
                print(" -> ".join(station_list))
                print()
    finally:
        conn.close()

def main():
    print_all_subway_routes()

if __name__ == "__main__":
    main()
