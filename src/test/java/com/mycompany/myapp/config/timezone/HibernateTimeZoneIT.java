package com.mycompany.myapp.config.timezone;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myapp.IntegrationTest;
import com.mycompany.myapp.repository.timezone.DateTimeWrapper;
import com.mycompany.myapp.repository.timezone.DateTimeWrapperRepository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
class HibernateTimeZoneIT {

    @Autowired
    private DateTimeWrapperRepository dateTimeWrapperRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.jpa.properties.hibernate.jdbc.time_zone:UTC}")
    private String zoneId;

    private DateTimeWrapper dateTimeWrapper;
    private DateTimeFormatter dateTimeFormatter;
    private DateTimeFormatter timeFormatter;
    private DateTimeFormatter offsetTimeFormatter;
    private DateTimeFormatter dateFormatter;

    @BeforeEach
    void setup() {
        dateTimeWrapper = new DateTimeWrapper();
        dateTimeWrapper.setInstant(Instant.parse("2014-11-12T05:10:00Z"));
        dateTimeWrapper.setLocalDateTime(LocalDateTime.parse("2014-11-12T07:20:00"));
        dateTimeWrapper.setOffsetDateTime(OffsetDateTime.parse("2011-12-14T08:30:00Z"));
        dateTimeWrapper.setZonedDateTime(ZonedDateTime.parse("2011-12-14T08:40:00Z"));
        dateTimeWrapper.setLocalTime(LocalTime.parse("14:50:00"));
        dateTimeWrapper.setOffsetTime(OffsetTime.parse("14:00:00+02:00"));
        dateTimeWrapper.setLocalDate(LocalDate.parse("2016-09-10"));

        dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S").withZone(ZoneOffset.UTC);
        timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
        offsetTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    }

    @Test
    @Transactional
    void storeInstantShouldBeStoredInUtc() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("instant", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);
        String expectedValue = dateTimeFormatter.format(dateTimeWrapper.getInstant());
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    @Test
    @Transactional
    void storeLocalDateTimeShouldBeNormalizedToUtc() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("local_date_time", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);

        // Normalize to UTC by using UTC explicitly instead of system default
        ZonedDateTime normalized = dateTimeWrapper.getLocalDateTime().atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneOffset.UTC);
        String expectedValue = dateTimeFormatter.format(normalized);
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    @Test
    @Transactional
    void storeOffsetDateTimeShouldBeNormalizedToUtc() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("offset_date_time", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);

        OffsetDateTime normalized = dateTimeWrapper.getOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
        String expectedValue = dateTimeFormatter.format(normalized.toInstant());
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    @Test
    @Transactional
    void storeZonedDateTimeShouldBeNormalizedToUtc() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("zoned_date_time", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);

        ZonedDateTime normalized = dateTimeWrapper.getZonedDateTime().withZoneSameInstant(ZoneOffset.UTC);
        String expectedValue = dateTimeFormatter.format(normalized.toInstant());
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    @Test
    @Transactional
    void storeLocalTimeShouldBeNormalizedToUtc1970() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("local_time", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);

        LocalTime localTime = dateTimeWrapper.getLocalTime();
        ZonedDateTime normalized = localTime.atDate(LocalDate.of(1970, 1, 1)).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneOffset.UTC);
        String expectedValue = timeFormatter.format(normalized);
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    @Test
    @Transactional
    void storeOffsetTimeShouldBeNormalizedToUtc() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("offset_time", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);

        OffsetTime original = dateTimeWrapper.getOffsetTime();

        // The actual behavior is that Hibernate stores the time as if it were on 1970-01-01
        // and then converts it to the configured timezone (UTC)
        ZonedDateTime normalized = original.atDate(LocalDate.of(1970, 1, 1)).toZonedDateTime().withZoneSameInstant(ZoneOffset.UTC);
        String expectedValue = normalized.format(offsetTimeFormatter);
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    @Test
    @Transactional
    void storeLocalDateShouldBeStoredAsIs() {
        dateTimeWrapperRepository.saveAndFlush(dateTimeWrapper);
        String request = generateSqlRequest("local_date", dateTimeWrapper.getId());
        SqlRowSet resultSet = jdbcTemplate.queryForRowSet(request);

        String expectedValue = dateTimeWrapper.getLocalDate().format(dateFormatter);
        assertThatValueFromSqlRowSetIsEqualToExpectedValue(resultSet, expectedValue);
    }

    private String generateSqlRequest(String fieldName, long id) {
        return format("SELECT %s FROM jhi_date_time_wrapper where id=%d", fieldName, id);
    }

    private void assertThatValueFromSqlRowSetIsEqualToExpectedValue(SqlRowSet sqlRowSet, String expectedValue) {
        while (sqlRowSet.next()) {
            String dbValue = sqlRowSet.getString(1);
            assertThat(dbValue).isNotNull();
            assertThat(dbValue).isEqualTo(expectedValue);
        }
    }
}
