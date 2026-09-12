package com.likelion.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:production-content;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionContentMigrationTests {
    private static final List<Content> EXPECTED = List.of(
        new Content("CH01", "성결대 멋사", 1, "안녕하세요 저희는 성결대 멋사 입니다"),
        new Content("CH01", "성결대 멋사", 2, "프론트엔드, 백엔드, 기획디자인 세 개의 부서가 있습니다"),
        new Content("CH01", "성결대 멋사", 3, "상상을 현실로 만드는 개발동아리 입니다"),
        new Content("CH01", "성결대 멋사", 4, "함께 공부하고 발전할 수 있습니다"),
        new Content("CH01", "성결대 멋사", 5, "저희의 아기사자가 되어주세요!"),
        new Content("CH02", "멋쟁이사자처럼", 1, "멋사에는 약 80개의 대학이 참여합니다"),
        new Content("CH02", "멋쟁이사자처럼", 2, "대표적인 활동으로는 해커톤이 있습니다"),
        new Content("CH02", "멋쟁이사자처럼", 3, "해커톤은 제한된 시간동안 집중적으로 기획, 개발하는 대회입니다"),
        new Content("CH02", "멋쟁이사자처럼", 4, "협력하는 방법을 키울 수 있습니다"),
        new Content("CH02", "멋쟁이사자처럼", 5, "저희의 아기사자가 되어주세요!"),
        new Content("CH03", "페스티벌 라디오", 1, "축제의 밤은 언제나 짧고 반짝인다."),
        new Content("CH03", "페스티벌 라디오", 2, "스피커가 울리면 모두 같은 편이 된다."),
        new Content("CH03", "페스티벌 라디오", 3, "조명이 꺼져도 노래는 남는다."),
        new Content("CH03", "페스티벌 라디오", 4, "오늘의 무대는 우리 모두의 것이다."),
        new Content("CH03", "페스티벌 라디오", 5, "마지막 곡까지 함께 달려보자."));

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void adminProperties(DynamicPropertyRegistry registry) {
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder().encode("test"));
    }

    @Test
    void cleanMigrationContainsExactProductionContent() {
        assertContent(jdbc);
    }

    @Test
    void v1DatabaseUpgradesToV2() {
        var dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:production-content-upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("1")).load().migrate();
        assertThat(new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM categories", Integer.class)).isZero();
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertContent(new JdbcTemplate(dataSource));
    }

    @Test
    void categoriesApiReturnsOnlyApprovedCategoryStructure() throws Exception {
        var response = mvc.perform(get("/api/categories"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var categories = json.readTree(response);

        assertThat(categories.size()).isEqualTo(3);
        assertThat(categories.get(0).get("code").asText()).isEqualTo("CH01");
        assertThat(categories.get(0).get("name").asText()).isEqualTo("성결대 멋사");
        assertThat(categories.get(1).get("code").asText()).isEqualTo("CH02");
        assertThat(categories.get(1).get("name").asText()).isEqualTo("멋쟁이사자처럼");
        assertThat(categories.get(2).get("code").asText()).isEqualTo("CH03");
        assertThat(categories.get(2).get("name").asText()).isEqualTo("페스티벌 라디오");
        assertThat(response).doesNotContain("phone");
    }

    private static void assertContent(JdbcTemplate jdbc) {
        var actual = jdbc.query("""
            SELECT c.code, c.name, s.sequence_number, s.content
            FROM categories c
            JOIN sentences s ON s.category_id = c.id
            ORDER BY c.code, s.sequence_number
            """, (rs, row) -> new Content(
                rs.getString("code"), rs.getString("name"),
                rs.getInt("sequence_number"), rs.getString("content")));

        assertThat(actual).containsExactlyElementsOf(EXPECTED);
        assertThat(actual).hasSize(15);
        assertThat(actual.stream().map(Content::code).distinct()).hasSize(3);
        assertThat(actual).filteredOn(content -> content.sequence() == 5).hasSize(3);
    }

    private record Content(String code, String name, int sequence, String content) {}
}
