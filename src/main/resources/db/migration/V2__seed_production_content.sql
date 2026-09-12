INSERT INTO categories (code, name) VALUES
    ('CH01', '성결대 멋사'),
    ('CH02', '멋쟁이사자처럼'),
    ('CH03', '페스티벌 라디오');

INSERT INTO sentences (category_id, sequence_number, content) VALUES
    ((SELECT id FROM categories WHERE code = 'CH01'), 1, '안녕하세요 저희는 성결대 멋사 입니다'),
    ((SELECT id FROM categories WHERE code = 'CH01'), 2, '프론트엔드, 백엔드, 기획디자인 세 개의 부서가 있습니다'),
    ((SELECT id FROM categories WHERE code = 'CH01'), 3, '상상을 현실로 만드는 개발동아리 입니다'),
    ((SELECT id FROM categories WHERE code = 'CH01'), 4, '함께 공부하고 발전할 수 있습니다'),
    ((SELECT id FROM categories WHERE code = 'CH01'), 5, '저희의 아기사자가 되어주세요!'),
    ((SELECT id FROM categories WHERE code = 'CH02'), 1, '멋사에는 약 80개의 대학이 참여합니다'),
    ((SELECT id FROM categories WHERE code = 'CH02'), 2, '대표적인 활동으로는 해커톤이 있습니다'),
    ((SELECT id FROM categories WHERE code = 'CH02'), 3, '해커톤은 제한된 시간동안 집중적으로 기획, 개발하는 대회입니다'),
    ((SELECT id FROM categories WHERE code = 'CH02'), 4, '협력하는 방법을 키울 수 있습니다'),
    ((SELECT id FROM categories WHERE code = 'CH02'), 5, '저희의 아기사자가 되어주세요!'),
    ((SELECT id FROM categories WHERE code = 'CH03'), 1, '축제의 밤은 언제나 짧고 반짝인다.'),
    ((SELECT id FROM categories WHERE code = 'CH03'), 2, '스피커가 울리면 모두 같은 편이 된다.'),
    ((SELECT id FROM categories WHERE code = 'CH03'), 3, '조명이 꺼져도 노래는 남는다.'),
    ((SELECT id FROM categories WHERE code = 'CH03'), 4, '오늘의 무대는 우리 모두의 것이다.'),
    ((SELECT id FROM categories WHERE code = 'CH03'), 5, '마지막 곡까지 함께 달려보자.');
