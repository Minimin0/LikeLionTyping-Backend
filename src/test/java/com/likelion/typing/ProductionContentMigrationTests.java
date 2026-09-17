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
        new Content("CH01", "성결대 멋사", 1, "안녕하세요 성결대학교 멋쟁이사자처럼입니다!"),
        new Content("CH01", "성결대 멋사", 2, "프론트엔드 백엔드 기획디자인 세 부서가 한 팀이 됩니다"),
        new Content("CH01", "성결대 멋사", 3, "상상을 코드로 아이디어를 현실로 만드는 개발동아리!"),
        new Content("CH01", "성결대 멋사", 4, "함께 고민하고 함께 성장합니다"),
        new Content("CH01", "성결대 멋사", 5, "저희의 아기사자가 되어주세요!"),
        new Content("CH02", "멋쟁이사자처럼", 1, "전국 약 80개 대학이 함께하는 멋쟁이사자처럼"),
        new Content("CH02", "멋쟁이사자처럼", 2, "대표 활동은? 바로 해커톤입니다"),
        new Content("CH02", "멋쟁이사자처럼", 3, "제한된 시간 폭발하는 아이디어!"),
        new Content("CH02", "멋쟁이사자처럼", 4, "오늘의 버그가 내일의 실력이 됩니다"),
        new Content("CH02", "멋쟁이사자처럼", 5, "당신의 도전을 기다립니다 아기사자님!"),
        new Content("CH03", "페스티벌 라디오", 1, "기다리던 동아리 페스티벌 오늘만큼은 마음껏 즐겨볼까요!"),
        new Content("CH03", "페스티벌 라디오", 2, "좋아하는 노래가 들려오면 친구와 함께 신나게 따라 불러보세요."),
        new Content("CH03", "페스티벌 라디오", 3, "처음 듣는 노래도 이런 날 들으면 왠지 좋아지는 것 같아요!"),
        new Content("CH03", "페스티벌 라디오", 4, "신나는 음악과 웃음소리가 가득한 지금 이 순간을 제대로 즐겨봐요."),
        new Content("CH03", "페스티벌 라디오", 5, "오늘 함께 들었던 노래와 추억은 오래 남을 거예요!"));

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
    void v1DatabaseUpgradesToLatestContent() {
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
