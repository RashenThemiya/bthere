package com.jobhub.migration;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SharedOptionAutoMergeTest {
    @Test void explicitAliasesMergeDifferentCodesAndRemapExistingReferences() throws Exception {
        try(var db=fixture()) {
            sql(db,"UPDATE service_custom_field SET code='CG_CARE_RIDE_VEHICLE_TYPE' WHERE field_id=20");
            new SharedOptionAutoMerge(db, SharedOptionAutoMerge.parseAliases("20:10")).apply();
            assertThat(scalar(db,"SELECT COUNT(*) FROM service_custom_field WHERE status='ACTIVE'")).isEqualTo("1");
            assertThat(scalar(db,"SELECT field_id FROM provider_custom_field_submission WHERE submission_id=2222")).isEqualTo("10");
            assertThat(scalar(db,"SELECT COUNT(*) FROM job_rate_condition WHERE field_id=20")).isEqualTo("0");
            assertThat(scalar(db,"SELECT COUNT(*) FROM service_option_map_marker_condition WHERE field_id=20")).isEqualTo("0");
            assertThat(scalar(db,"SELECT verification_status FROM provider_service_option")).isEqualTo("APPROVED");
            assertThat(scalar(db,"SELECT payload_json FROM shared_option_merge_archive WHERE table_name='service_custom_field' AND payload_json LIKE '%CG_CARE_RIDE_VEHICLE_TYPE%'")).contains("CG_CARE_RIDE_VEHICLE_TYPE");
        }
    }

    @Test void invalidAliasesRollbackWithoutMergingUnrelatedDefinitions() throws Exception {
        try(var db=fixture()) {
            assertThatThrownBy(()->new SharedOptionAutoMerge(db, java.util.Map.of(10L,999L)).apply())
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(scalar(db,"SELECT COUNT(*) FROM service_custom_field WHERE status='ACTIVE'")).isEqualTo("2");
            assertThat(scalar(db,"SELECT COUNT(*) FROM shared_option_merge_archive")).isEqualTo("0");
        }
        assertThatThrownBy(()->SharedOptionAutoMerge.parseAliases("5:7,5:8")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mergesDuplicateConfigurationAndPreservesHistoryAndDistinctCombinations() throws Exception {
        try(var db=fixture()) {
            var report=new SharedOptionAutoMerge(db).apply();
            assertThat(report.get("mergedFields")).isEqualTo(1);
            assertThat(scalar(db,"SELECT COUNT(*) FROM service_custom_field WHERE status='ACTIVE'")).isEqualTo("1");
            String choices=scalar(db,"SELECT options_json FROM service_custom_field WHERE field_id=10");
            assertThat(choices).contains("CAR","BIKE","bike.svg");
            assertThat(scalar(db,"SELECT map_marker_field_id FROM service_option WHERE option_id=1")).isEqualTo("10");
            assertThat(scalar(db,"SELECT field_id FROM provider_custom_field_submission WHERE submission_id=2222")).isEqualTo("10");
            assertThat(scalar(db,"SELECT COUNT(*) FROM provider_service_option")).isEqualTo("1");
            assertThat(scalar(db,"SELECT assignment_id FROM provider_service_option")).isEqualTo("22");
            assertThat(scalar(db,"SELECT verification_status FROM provider_service_option")).isEqualTo("APPROVED");
            assertThat(scalar(db,"SELECT value_json FROM provider_custom_field_submission WHERE assignment_id=22 AND field_id=10")).contains("CAR","BIKE");
            assertThat(scalar(db,"SELECT status FROM job_rate WHERE rate_id=72")).isEqualTo("INACTIVE");
            assertThat(scalar(db,"SELECT status FROM job_rate WHERE rate_id=73")).isEqualTo("ACTIVE");
            assertThat(scalar(db,"SELECT COUNT(*) FROM job_rate_condition WHERE field_id=20")).isEqualTo("0");
            assertThat(scalar(db,"SELECT status FROM service_option_map_marker_rule WHERE marker_rule_id=82")).isEqualTo("INACTIVE");
            assertThat(scalar(db,"SELECT COUNT(*) FROM service_type_document_requirement")).isEqualTo("1");
            assertThat(scalar(db,"SELECT minimum_age FROM customer_eligibility_rule")).isEqualTo("21");
            assertThat(scalar(db,"SELECT required_codes_json FROM customer_eligibility_rule")).contains("NIC","DOB");
            assertThat(scalar(db,"SELECT pricing_snapshot_json FROM job WHERE job_id=99")).isEqualTo("original-booking-snapshot");
            assertThat(scalar(db,"SELECT payload_json FROM shared_option_merge_archive WHERE table_name='service_custom_field' AND payload_json LIKE '%\"field_id\":20%'")).contains("VEHICLE_TYPE","bike.svg");
            String archives=scalar(db,"SELECT COUNT(*) FROM shared_option_merge_archive");
            assertThat(new SharedOptionAutoMerge(db).apply()).containsEntry("alreadyMerged",1);
            assertThat(scalar(db,"SELECT COUNT(*) FROM shared_option_merge_archive")).isEqualTo(archives);
        }
    }

    @Test void latestActualRejectionIsNotPromotedByAnOlderApprovedRegistration() throws Exception {
        try(var db=fixture()) {
            sql(db,"UPDATE provider_service_option SET verification_status='REJECTED' WHERE provider_option_id=2");
            new SharedOptionAutoMerge(db).apply();
            assertThat(scalar(db,"SELECT verification_status FROM provider_service_option")).isEqualTo("REJECTED");
            assertThat(scalar(db,"SELECT reviewed_by FROM provider_service_option")).isEqualTo("7");
        }
    }

    @Test void incompatibleAnswersAreArchivedAndPendingEvidenceIsNotAutoApproved() throws Exception {
        try(var db=fixture()) {
            sql(db,"UPDATE service_custom_field SET field_type='NUMBER' WHERE field_id=20");
            sql(db,"UPDATE provider_custom_field_submission SET value_json='100' WHERE submission_id=2222");
            new SharedOptionAutoMerge(db).apply();
            assertThat(scalar(db,"SELECT COUNT(*) FROM provider_custom_field_submission WHERE submission_id=2222")).isEqualTo("0");
            assertThat(scalar(db,"SELECT verification_status FROM provider_service_option")).isEqualTo("PENDING");
            assertThat(scalar(db,"SELECT COUNT(*) FROM shared_option_merge_archive WHERE table_name='provider_custom_field_submission'")).isEqualTo("2");
        }
        try(var db=fixture()) {
            sql(db,"UPDATE provider_custom_field_submission SET verification_status='PENDING' WHERE submission_id=2222");
            new SharedOptionAutoMerge(db).apply();
            assertThat(scalar(db,"SELECT verification_status FROM provider_service_option")).isEqualTo("PENDING");
        }
    }

    @Test void dataErrorsRollbackInsteadOfLeavingHalfMergedConfiguration() throws Exception {
        try(var db=fixture()) {
            sql(db,"INSERT INTO provider_service_option VALUES(3,NULL,999,1,'ACTIVE','ACTIVE','PENDING',NULL,NULL,'2026-10-03 00:00:00')");
            assertThatThrownBy(()->new SharedOptionAutoMerge(db).apply()).isInstanceOf(SQLException.class);
            assertThat(scalar(db,"SELECT COUNT(*) FROM service_custom_field WHERE status='ACTIVE'")).isEqualTo("2");
            assertThat(scalar(db,"SELECT COUNT(*) FROM shared_option_merge_archive")).isEqualTo("0");
            assertThat(scalar(db,"SELECT COUNT(*) FROM shared_option_merge_state")).isEqualTo("0");
        }
    }

    @Test void sameRadiusPricesInSeparateAreasDoNotLoseTheirDistinctCoverage() throws Exception {
        try(var db=fixture()) {
            sql(db,"UPDATE job_rate SET area_radius_km=10,area_latitude=0,area_longitude=0 WHERE rate_id=71");
            sql(db,"UPDATE job_rate SET area_radius_km=10,area_latitude=20,area_longitude=20 WHERE rate_id=72");
            new SharedOptionAutoMerge(db).apply();
            assertThat(scalar(db,"SELECT COUNT(*) FROM job_rate WHERE status='ACTIVE'")).isEqualTo("3");
        }
    }

    private Connection fixture() throws Exception {
        var db=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL");
        sql(db,"""
            CREATE TABLE service_option(option_id BIGINT PRIMARY KEY,service_provider_type_id BIGINT,display_order INT,status VARCHAR(30),map_marker_field_id BIGINT);
            CREATE TABLE service_type_option(option_id BIGINT,service_provider_type_id BIGINT,display_order INT,status VARCHAR(30),map_marker_field_id BIGINT,PRIMARY KEY(option_id,service_provider_type_id));
            CREATE TABLE service_custom_field(field_id BIGINT PRIMARY KEY,service_provider_type_id BIGINT,option_id BIGINT,scope VARCHAR(40),code VARCHAR(100),field_type VARCHAR(30),`required` BOOLEAN,requires_approval BOOLEAN,options_json LONGTEXT,condition_json LONGTEXT,customer_visible BOOLEAN,searchable BOOLEAN,value_source VARCHAR(30),profile_field VARCHAR(40),version INT,status VARCHAR(30),updated_at TIMESTAMP);
            CREATE TABLE service_custom_field_option(field_id BIGINT,option_id BIGINT,PRIMARY KEY(field_id,option_id));
            CREATE TABLE service_provider_type_assignment(assignment_id BIGINT PRIMARY KEY,service_provider_id BIGINT,service_provider_type_id BIGINT);
            CREATE TABLE provider_service_option(provider_option_id BIGINT PRIMARY KEY,provider_id BIGINT,assignment_id BIGINT,option_id BIGINT,provider_status VARCHAR(30),admin_status VARCHAR(30),verification_status VARCHAR(30),reviewed_by BIGINT,reviewed_at TIMESTAMP,updated_at TIMESTAMP);
            CREATE TABLE provider_custom_field_submission(submission_id BIGINT AUTO_INCREMENT PRIMARY KEY,assignment_id BIGINT,field_id BIGINT,provider_id BIGINT,service_provider_type_id BIGINT,reusable_code VARCHAR(100),reusable_field_type VARCHAR(30),reuse_scope VARCHAR(30),value_json LONGTEXT,verification_status VARCHAR(30),reviewed_by BIGINT,reviewed_at TIMESTAMP,updated_at TIMESTAMP,review_note VARCHAR(1000),UNIQUE(assignment_id,field_id));
            CREATE TABLE job_rate(rate_id BIGINT PRIMARY KEY,service_provider_type_id BIGINT,service_option_id BIGINT,market_id BIGINT,status VARCHAR(30),rate DECIMAL(12,2),effective_from TIMESTAMP,effective_to TIMESTAMP,area_radius_km DECIMAL(12,2),area_latitude DECIMAL(12,6),area_longitude DECIMAL(12,6),updated_at TIMESTAMP);
            CREATE TABLE job_rate_condition(rate_id BIGINT,field_id BIGINT,choice_value VARCHAR(255),PRIMARY KEY(rate_id,field_id));
            CREATE TABLE service_option_map_marker_rule(marker_rule_id BIGINT PRIMARY KEY,service_provider_type_id BIGINT,option_id BIGINT,status VARCHAR(30),object_key VARCHAR(255),updated_at TIMESTAMP);
            CREATE TABLE service_option_map_marker_condition(marker_rule_id BIGINT,field_id BIGINT,choice_value VARCHAR(255),PRIMARY KEY(marker_rule_id,field_id));
            CREATE TABLE service_type_document_requirement(requirement_id BIGINT PRIMARY KEY,service_provider_type_id BIGINT,option_id BIGINT,document_type_id BIGINT,mandatory BOOLEAN,requires_approval BOOLEAN,UNIQUE(service_provider_type_id,option_id,document_type_id));
            CREATE TABLE customer_eligibility_rule(rule_id BIGINT PRIMARY KEY,service_type_id BIGINT,option_id BIGINT,scope VARCHAR(30),target_key VARCHAR(255) UNIQUE,minimum_age INT,date_of_birth_code VARCHAR(100),requires_verified_date_of_birth BOOLEAN,enabled BOOLEAN,required_codes_json LONGTEXT);
            CREATE TABLE service_provider_document(document_id BIGINT PRIMARY KEY,service_provider_id BIGINT,document_type_id BIGINT,verification_status VARCHAR(30),expiry_date DATE);
            CREATE TABLE job(job_id BIGINT PRIMARY KEY,pricing_snapshot_json LONGTEXT);
            INSERT INTO service_option VALUES(1,1,0,'ACTIVE',NULL);
            INSERT INTO service_type_option VALUES(1,1,0,'ACTIVE',10),(1,2,8,'ACTIVE',20);
            INSERT INTO service_custom_field VALUES(10,1,NULL,'PROVIDER_REQUIREMENT','VEHICLE_TYPE','MULTI_SELECT',TRUE,TRUE,'["CAR"]',NULL,TRUE,TRUE,'MANUAL',NULL,1,'ACTIVE','2026-10-01 00:00:00');
            INSERT INTO service_custom_field VALUES(20,2,NULL,'PROVIDER_REQUIREMENT','VEHICLE_TYPE','MULTI_SELECT',TRUE,TRUE,'[{"value":"BIKE","icon":{"key":"bike.svg","url":"https://example.com/bike.svg"}}]',NULL,TRUE,TRUE,'MANUAL',NULL,1,'ACTIVE','2026-10-02 00:00:00');
            INSERT INTO service_custom_field_option VALUES(10,1),(20,1);
            INSERT INTO service_provider_type_assignment VALUES(11,15,1),(22,15,2);
            INSERT INTO provider_service_option VALUES(1,NULL,11,1,'ACTIVE','ACTIVE','APPROVED',6,'2026-10-01 00:00:00','2026-10-01 00:00:00'),(2,NULL,22,1,'ACTIVE','ACTIVE','APPROVED',7,'2026-10-02 00:00:00','2026-10-02 00:00:00');
            INSERT INTO provider_custom_field_submission VALUES(1111,11,10,15,1,'VEHICLE_TYPE','MULTI_SELECT','ASSIGNMENT','["CAR"]','APPROVED',6,'2026-10-01 00:00:00','2026-10-01 00:00:00',NULL),(2222,22,20,15,2,'VEHICLE_TYPE','MULTI_SELECT','ASSIGNMENT','["BIKE"]','APPROVED',7,'2026-10-02 00:00:00','2026-10-02 00:00:00',NULL);
            INSERT INTO job_rate VALUES(71,1,1,1,'ACTIVE',1000,NULL,NULL,NULL,NULL,NULL,'2026-10-01 00:00:00'),(72,2,1,1,'ACTIVE',2000,NULL,NULL,NULL,NULL,NULL,'2026-10-02 00:00:00'),(73,2,1,1,'ACTIVE',1500,NULL,NULL,NULL,NULL,NULL,'2026-10-02 00:00:00');
            INSERT INTO job_rate_condition VALUES(71,10,'CAR'),(72,20,'CAR'),(73,20,'BIKE');
            INSERT INTO service_option_map_marker_rule VALUES(81,1,1,'ACTIVE','car.svg','2026-10-01 00:00:00'),(82,2,1,'ACTIVE','car-alias.svg','2026-10-02 00:00:00');
            INSERT INTO service_option_map_marker_condition VALUES(81,10,'CAR'),(82,20,'CAR');
            INSERT INTO service_type_document_requirement VALUES(1,1,1,4,FALSE,FALSE),(2,2,1,4,TRUE,TRUE);
            INSERT INTO service_provider_document VALUES(1,15,4,'APPROVED',NULL);
            INSERT INTO customer_eligibility_rule VALUES(1,1,1,'OPTION','OPTION:1:1',18,'DOB',FALSE,TRUE,'["NIC"]'),(2,2,1,'OPTION','OPTION:2:1',21,'DOB',TRUE,TRUE,'["DOB"]');
            INSERT INTO job VALUES(99,'original-booking-snapshot');
            """);
        return db;
    }
    private void sql(Connection db,String sql) throws SQLException {try(var st=db.createStatement()){for(String part:sql.split(";"))if(!part.isBlank())st.execute(part);}}
    private String scalar(Connection db,String sql) throws SQLException {try(var st=db.createStatement();var rs=st.executeQuery(sql)){assertThat(rs.next()).isTrue();return rs.getString(1);}}
}
