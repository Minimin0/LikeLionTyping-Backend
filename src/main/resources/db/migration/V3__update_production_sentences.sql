UPDATE sentences
SET content = CASE sequence_number
    WHEN 1 THEN '안녕하세요 성결대학교 멋쟁이사자처럼입니다!'
    WHEN 2 THEN '프론트엔드 백엔드 기획디자인 세 부서가 한 팀이 됩니다'
    WHEN 3 THEN '상상을 코드로 아이디어를 현실로 만드는 개발동아리!'
    WHEN 4 THEN '함께 고민하고 함께 성장합니다'
    WHEN 5 THEN '저희의 아기사자가 되어주세요!'
END
WHERE category_id = (SELECT id FROM categories WHERE code = 'CH01');

UPDATE sentences
SET content = CASE sequence_number
    WHEN 1 THEN '전국 약 80개 대학이 함께하는 멋쟁이사자처럼'
    WHEN 2 THEN '대표 활동은? 바로 해커톤입니다'
    WHEN 3 THEN '제한된 시간 폭발하는 아이디어!'
    WHEN 4 THEN '오늘의 버그가 내일의 실력이 됩니다'
    WHEN 5 THEN '당신의 도전을 기다립니다 아기사자님!'
END
WHERE category_id = (SELECT id FROM categories WHERE code = 'CH02');

UPDATE sentences
SET content = CASE sequence_number
    WHEN 1 THEN '기다리던 동아리 페스티벌 오늘만큼은 마음껏 즐겨볼까요!'
    WHEN 2 THEN '좋아하는 노래가 들려오면 친구와 함께 신나게 따라 불러보세요.'
    WHEN 3 THEN '처음 듣는 노래도 이런 날 들으면 왠지 좋아지는 것 같아요!'
    WHEN 4 THEN '신나는 음악과 웃음소리가 가득한 지금 이 순간을 제대로 즐겨봐요.'
    WHEN 5 THEN '오늘 함께 들었던 노래와 추억은 오래 남을 거예요!'
END
WHERE category_id = (SELECT id FROM categories WHERE code = 'CH03');
