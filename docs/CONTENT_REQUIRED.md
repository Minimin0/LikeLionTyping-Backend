# Production Content

승인된 운영 콘텐츠는 `V2__seed_production_content.sql`이 관리합니다.

- CH01: 성결대 멋사
- CH02: 멋쟁이사자처럼
- CH03: 페스티벌 라디오

각 카테고리의 sequence 1~5 문장은 migration에 있는 승인 원문을 그대로 사용합니다. 배포 전 다음 쿼리 결과가 CH01~CH03 각각 5인지 확인합니다.

```sql
SELECT c.code, COUNT(s.id) AS sentence_count
FROM categories c
LEFT JOIN sentences s ON s.category_id = c.id
GROUP BY c.id, c.code
ORDER BY c.code;
```

운영 DB에서 직접 수정하지 말고 검토된 Flyway migration으로 배포합니다.
