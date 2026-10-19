package com.jobhub.service.provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.admin.ProviderApprovalApi.*;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.audit.AuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProviderApprovalServiceTest {
 @Mock ServiceProviderRepository providers;
 @Mock ServiceProviderTypeAssignmentRepository assignments;
 @Mock ServiceProviderTypeRepository types;
 @Mock ServiceTypeDocumentRequirementRepository documentRequirements;
 @Mock DocumentTypeRepository documentTypes;
 @Mock ServiceProviderDocumentRepository documents;
 @Mock ServiceTypeSkillRepository skillDefinitions;
 @Mock ProviderServiceSkillRepository skills;
 @Mock ProviderProfessionalCertificateRepository certificates;
 @Mock ProviderEducationQualificationRepository education;
 @Mock ServiceCustomFieldRepository fields;
 @Mock ProviderCustomFieldSubmissionRepository submissions;
 @Mock ProviderServiceOptionRepository providerOptions;
 @Mock ServiceOptionRepository options;
 @Mock ProviderServiceApprovalService approvals;
 @Mock ProviderDocumentService documentReviews;
 @Mock ProviderQualificationService qualificationReviews;
 @Mock ServiceCustomFieldService customReviews;
 @Mock ServiceOptionService optionReviews;
 @Mock AuditService audit;
 @Mock EntityManager entityManager;
 @Spy ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
 @InjectMocks AdminProviderApprovalService service;
 ServiceProvider p; ServiceProviderTypeAssignment a; ServiceProviderType type;
 @BeforeEach void setup() {
  p=new ServiceProvider();p.setServiceProviderId(12L);p.setFirstName("Rashen");p.setLastName("Themiy");
  a=new ServiceProviderTypeAssignment();a.setAssignmentId(14L);a.setServiceProviderId(12L);a.setServiceProviderTypeId(3L);a.setVerificationStatus("PENDING");
  type=new ServiceProviderType();type.setServiceProviderTypeId(3L);type.setName("Home Nurse");
  lenient().when(providers.findById(12L)).thenReturn(Optional.of(p));
  lenient().when(providers.lockById(12L)).thenReturn(Optional.of(p));
  lenient().when(assignments.findAllByServiceProviderId(12L)).thenReturn(List.of(a));
  lenient().when(assignments.findById(14L)).thenReturn(Optional.of(a));
  lenient().when(types.findById(3L)).thenReturn(Optional.of(type));
 }
 private ProviderServiceSkill pendingSkill(long id) {
  var def=new ServiceTypeSkill();def.setSkillId(4L);def.setName("First aid");def.setMandatory(true);def.setRequiresApproval(true);
  var item=new ProviderServiceSkill();item.setProviderSkillId(id);item.setAssignmentId(14L);item.setSkillId(4L);item.setVerificationStatus("PENDING");
  when(skillDefinitions.findAllByServiceProviderTypeIdOrderByNameAsc(3L)).thenReturn(List.of(def));
  when(skills.findAllByAssignmentId(14L)).thenReturn(List.of(item));
  lenient().when(entityManager.find(ProviderServiceSkill.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(item);
  return item;
 }
 @Test void linkedProfileDataIsNotMarkedMissingJustBecauseItHasNoSubmissionId() {
  var field=new ServiceCustomField();field.setFieldId(4L);field.setCode("GENDER");field.setLabel("Gender");
  field.setValueSource("PROVIDER_PROFILE");field.setProfileField("gender");field.setRequired(true);
  when(fields.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(3L,"PROVIDER_REQUIREMENT","ACTIVE"))
      .thenReturn(List.of(field));
  when(approvals.conditionApplies(eq(a),eq(field),anyMap(),anyMap())).thenReturn(true);
  when(approvals.profileValue(a,field)).thenReturn(new ProviderProfileFields.Resolved(com.fasterxml.jackson.databind.node.TextNode.valueOf("FEMALE"),"PROVIDED"));
  var row=service.detail(12L).services().get(0).dynamicRequirements().get(0);
  assertThat(row.id()).isNull();assertThat(row.missing()).isFalse();assertThat(row.reviewable()).isFalse();
  assertThat(row.verificationStatus()).isEqualTo("PROVIDED");
  assertThat(row.details().path("valueSource").asText()).isEqualTo("PROVIDER_PROFILE");
 }
 @Test void showsMissingRequirementsAndExpiredUploads() {
  type.setCertificateRequirement("REQUIRED");type.setEducationRequirement("REQUIRED");type.setOptionsRequired(true);
  var def=new ServiceTypeSkill();def.setSkillId(4L);def.setName("First aid");def.setMandatory(true);
  when(skillDefinitions.findAllByServiceProviderTypeIdOrderByNameAsc(3L)).thenReturn(List.of(def));
  var docDef=new ServiceTypeDocumentRequirement();docDef.setRequirementId(8L);docDef.setDocumentTypeId(7L);docDef.setMandatory(true);docDef.setRequiresApproval(true);
  var upload=new ServiceProviderDocument();upload.setDocumentId(70L);upload.setDocumentTypeId(7L);upload.setVerificationStatus("PENDING");upload.setExpiryDate(LocalDate.now().minusDays(1));
  when(documentRequirements.findAllByServiceProviderTypeId(3L)).thenReturn(List.of(docDef));
  when(documents.findAllByServiceProviderIdOrderByCreatedAtDesc(12L)).thenReturn(List.of(upload));
  var detail=service.detail(12L);var s=detail.services().get(0);
  assertThat(s.missingCount()).isEqualTo(4);assertThat(s.documents().get(0).expired()).isTrue();
  assertThat(s.documents().get(0).reviewable()).isFalse();assertThat(s.skills().get(0).id()).isNull();
  assertThat(s.skills().get(0).verificationStatus()).isEqualTo("MISSING");
  assertThat(s.checks()).containsEntry("availability",false);
 }
 @Test void paginatesProvidersInsteadOfAssignmentsAndDeduplicatesSharedDocumentCount() {
  var other=new ServiceProviderTypeAssignment();other.setAssignmentId(15L);other.setServiceProviderId(12L);other.setServiceProviderTypeId(3L);
  when(assignments.findAllByServiceProviderId(12L)).thenReturn(List.of(a,other));
  var def=new ServiceTypeDocumentRequirement();def.setRequirementId(8L);def.setDocumentTypeId(7L);def.setRequiresApproval(true);
  var doc=new ServiceProviderDocument();doc.setDocumentId(70L);doc.setDocumentTypeId(7L);doc.setVerificationStatus("PENDING");
  when(documentRequirements.findAllByServiceProviderTypeId(3L)).thenReturn(List.of(def));when(documents.findAllByServiceProviderIdOrderByCreatedAtDesc(12L)).thenReturn(List.of(doc));
  var pageable=PageRequest.of(0,20);when(providers.findApprovalProviders("PENDING",pageable)).thenReturn(new PageImpl<>(List.of(p),pageable,1));
  var result=service.list("PENDING",pageable);
  assertThat(result.getTotalElements()).isEqualTo(1);assertThat(result.getContent().get(0).pendingCount()).isEqualTo(1);
  assertThat(result.getContent().get(0).services()).hasSize(2);
 }
 @Test void bulkPrevalidatesAllOwnershipBeforeAnyReview() {
  pendingSkill(60L);
  when(entityManager.find(ProviderServiceSkill.class,999L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(new ProviderServiceSkill());
  assertThatThrownBy(() -> service.bulk(1L,new BulkRequest(14L,List.of(new Review("SKILL",60L,"APPROVED",null),new Review("SKILL",999L,"APPROVED",null)))))
      .isInstanceOf(ResourceNotFoundException.class);
  verifyNoInteractions(qualificationReviews,audit);
 }
 @Test void rejectsDuplicatesRejectionWithoutReasonAndAlreadyReviewedItems() {
  pendingSkill(60L);var r=new Review("SKILL",60L,"APPROVED",null);
  assertThatThrownBy(() -> service.bulk(1L,new BulkRequest(14L,List.of(r,r)))).hasMessageContaining("Duplicate");
  assertThatThrownBy(() -> service.bulk(1L,new BulkRequest(14L,List.of(new Review("SKILL",60L,"REJECTED"," "))))).hasMessageContaining("reason");
  var item=pendingSkill(60L);item.setVerificationStatus("APPROVED");
  assertThatThrownBy(() -> service.bulk(1L,new BulkRequest(14L,List.of(r)))).isInstanceOf(ConflictException.class);
  verifyNoInteractions(qualificationReviews);
 }
 @Test void refreshesProviderAndReturnsAtomicResultAfterReview() {
  var item=pendingSkill(60L);
  when(qualificationReviews.reviewSkill(eq(60L),eq(1L),any())).thenAnswer(i -> {item.setVerificationStatus("APPROVED");return null;});
  var result=service.bulk(1L,new BulkRequest(14L,List.of(new Review("SKILL",60L,"APPROVED",null))));
  assertThat(result.atomic()).isTrue();assertThat(result.reviewedCount()).isEqualTo(1);assertThat(result.provider().pendingCount()).isZero();
  verify(approvals).refreshProviderApprovals(12L);verify(audit).record(eq(1L),eq("PROVIDER_APPROVAL_BULK_REVIEW"),anyString(),eq(14L),isNull(),any());
 }
 @Test void transactionInterceptorRollsBackIfDelegatedReviewFails() {
  pendingSkill(60L);
  var def=new ServiceTypeDocumentRequirement();def.setRequirementId(8L);def.setDocumentTypeId(7L);def.setRequiresApproval(true);
  var doc=new ServiceProviderDocument();doc.setDocumentId(70L);doc.setDocumentTypeId(7L);doc.setVerificationStatus("PENDING");
  when(documentRequirements.findAllByServiceProviderTypeId(3L)).thenReturn(List.of(def));
  when(documents.findAllByServiceProviderIdOrderByCreatedAtDesc(12L)).thenReturn(List.of(doc));
  when(entityManager.find(ServiceProviderDocument.class,70L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(doc);
  when(qualificationReviews.reviewSkill(anyLong(),anyLong(),any())).thenThrow(new ConflictException("Concurrent review"));
  var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
  var tx=new org.springframework.transaction.support.SimpleTransactionStatus();when(manager.getTransaction(any())).thenReturn(tx);
  var interceptor=new org.springframework.transaction.interceptor.TransactionInterceptor(manager,new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
  var factory=new org.springframework.aop.framework.ProxyFactory(service);factory.addAdvice(interceptor);
  var proxy=(AdminProviderApprovalService)factory.getProxy();
  assertThatThrownBy(() -> proxy.bulk(1L,new BulkRequest(14L,List.of(new Review("DOCUMENT",70L,"APPROVED",null),new Review("SKILL",60L,"APPROVED",null))))).isInstanceOf(ConflictException.class);
  verify(documentReviews).review(eq(70L),eq(1L),any());
  verify(manager).rollback(tx);verify(manager,never()).commit(any());verifyNoInteractions(audit);
 }
 @Test void dynamicRequirementsUseExistingConditionsAndApprovedReuse() {
  var field=new ServiceCustomField();field.setFieldId(4L);field.setCode("NIC");field.setLabel("NIC");field.setFieldType("FILE");field.setRequired(true);field.setRequiresApproval(true);
  var reused=new ProviderCustomFieldSubmission();reused.setSubmissionId(81L);reused.setAssignmentId(15L);reused.setVerificationStatus("APPROVED");reused.setValueJson("{\"key\":\"test/document\"}");
  when(fields.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(3L,"PROVIDER_REQUIREMENT","ACTIVE")).thenReturn(List.of(field));
  when(approvals.conditionApplies(eq(a),eq(field),anyMap(),anyMap())).thenReturn(true);when(approvals.reusableSubmission(a,field)).thenReturn(Optional.of(reused));
  var requirement=service.detail(12L).services().get(0).dynamicRequirements().get(0);
  assertThat(requirement.missing()).isFalse();assertThat(requirement.reviewable()).isFalse();assertThat(requirement.sourceAssignmentId()).isEqualTo(15L);
  assertThat(requirement.details().path("reused").asBoolean()).isTrue();
 }
 @Test void invalidFilterAndMissingProviderAreRejected() {
  assertThatThrownBy(() -> service.list("typo",PageRequest.of(0,20))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> service.detail(999L)).isInstanceOf(ResourceNotFoundException.class);
 }
}
