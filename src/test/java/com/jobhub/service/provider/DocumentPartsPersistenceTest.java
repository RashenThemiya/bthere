package com.jobhub.service.provider;

import com.jobhub.entity.provider.*;
import com.jobhub.dto.provider.*;
import org.hibernate.cfg.Configuration;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DocumentPartsPersistenceTest {
    @Test void storesAllPartsAndKeepsExistingApprovedEvidenceWhenConfigurationChanges() {
        try(var factory=new Configuration().addAnnotatedClass(DocumentType.class)
                .addAnnotatedClass(ServiceProviderDocument.class)
                .setPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy())
                .setProperty("hibernate.connection.driver_class","org.h2.Driver")
                .setProperty("hibernate.connection.url","jdbc:h2:mem:"+UUID.randomUUID())
                .setProperty("hibernate.hbm2ddl.auto","create-drop").buildSessionFactory();
            var em=factory.createEntityManager()) {
            em.getTransaction().begin();
            var type=new DocumentType(); type.setName("Licence"); type.setStatus("ACTIVE");
            em.persist(type);
            var legacy=new ServiceProviderDocument();legacy.setServiceProviderId(1L);
            legacy.setDocumentTypeId(type.getDocumentTypeId());legacy.setDocumentUrl("providers/1/documents/legacy.jpg");
            legacy.setVerificationStatus("APPROVED");legacy.setVerifiedBy(7L);em.persist(legacy);
            var definitions=List.of(new DocumentPartDefinition("FRONT","Front",true,1,5,List.of("image/jpeg")),
                    new DocumentPartDefinition("BACK","Back",true,1,5,List.of("image/jpeg")));
            type.setPartsJson(DocumentParts.write(definitions));
            var bundle=new ServiceProviderDocument();bundle.setServiceProviderId(2L);
            bundle.setDocumentTypeId(type.getDocumentTypeId());bundle.setVerificationStatus("PENDING");
            bundle.setPartsDefinitionJson(type.getPartsJson());
            bundle.setPartsJson(DocumentParts.write(List.of(
                    new DocumentPartFiles("FRONT",List.of("providers/2/documents/front.jpg")),
                    new DocumentPartFiles("BACK",List.of("providers/2/documents/back.jpg")))));
            em.persist(bundle);em.flush();em.clear();
            var stored=em.find(ServiceProviderDocument.class,bundle.getDocumentId());
            assertThat(stored.effectiveParts()).hasSize(2);
            assertThat(DocumentParts.definitions(stored.getPartsDefinitionJson())).isEqualTo(definitions);
            var old=em.find(ServiceProviderDocument.class,legacy.getDocumentId());
            assertThat(old.effectiveParts()).isEmpty();assertThat(old.getVerificationStatus()).isEqualTo("APPROVED");
            assertThat(old.getVerifiedBy()).isEqualTo(7L);
            em.getTransaction().rollback();
        }
    }
}
