import pymysql

def fix_subwaydata_2ho():
    # 1) DB 연결 정보
    conn = pymysql.connect(
        host='localhost',
        user='root',
        password='1234',
        db='test'
    )

    try:
        with conn.cursor() as cursor:
            # 2) linenum = '2호선' 행들 SELECT
            select_sql = """
                SELECT 
                    id, 
                    front_stationanme, 
                    back_stationanme,
                    front_outercode,
                    back_outercode
                FROM subway_data
                WHERE linenum = '2호선'
            """
            cursor.execute(select_sql)
            rows = cursor.fetchall()  # [(id, front_name, back_name, front_code, back_code), ...]

            # 3) 각 행에 대해 swap & UPDATE
            update_sql = """
                UPDATE subway_data
                SET 
                    front_stationanme = %s,
                    back_stationanme = %s,
                    front_outercode = %s,
                    back_outercode = %s
                WHERE id = %s
            """

            for row in rows:
                row_id = row[0]
                old_front_name = row[1]
                old_back_name  = row[2]
                old_front_code = row[3]
                old_back_code  = row[4]

                # swap
                new_front_name = old_back_name
                new_back_name  = old_front_name
                new_front_code = old_back_code
                new_back_code  = old_front_code

                cursor.execute(update_sql, (
                    new_front_name,
                    new_back_name,
                    new_front_code,
                    new_back_code,
                    row_id
                ))
            
            conn.commit()
            print("2호선 front/back 역명, outercode 스왑 완료.")
    finally:
        conn.close()

if __name__ == "__main__":
    fix_subwaydata_2ho()
