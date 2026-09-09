# Production Content Required

현재 저장소와 기획서에는 CH01~CH03의 확정 이름과 각 5개 문장이 없습니다. 가짜 운영 데이터를 migration에 넣지 않았으므로 배포 전 팀이 아래 18개 값을 확정해야 합니다.

- CH01, CH02, CH03의 표시 이름
- 각 카테고리의 sequence 1~5 문장, 총 15개

확정 후 `V2__seed_production_content.sql` Flyway migration을 추가합니다. 각 카테고리를 먼저 넣고 code로 ID를 조회해 문장을 넣으며, 한 트랜잭션에서 실행합니다. 배포 전 다음 쿼리 결과가 CH01~CH03 각각 5인지 확인합니다.

```sql
SELECT c.code, COUNT(s.id) AS sentence_count
FROM categories c
LEFT JOIN sentences s ON s.category_id = c.id
GROUP BY c.id, c.code
ORDER BY c.code;
```

운영 DB에서 직접 수정하지 말고 검토된 Flyway migration으로 배포합니다.
