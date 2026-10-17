package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobhub.dto.admin.ProviderApprovalApi.*;
import com.jobhub.dto.provider.DocumentReviewRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.exception.*;
import com.jobhub.repository.provider.*;
import com.jobhub.service.audit.AuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class AdminProviderApprovalService {
 private final ServiceProviderRepository providers;
 private final ServiceProviderTypeAssignmentRepository assignments;
 private final ServiceProviderTypeRepository types;
 private final ServiceTypeDocumentRequirementRepository documentRequirements;
 private final DocumentTypeRepository documentTypes;
 private final ServiceProviderDocumentRepository documents;
 private final ServiceTypeSkillRepository skillDefinitions;
 private final ProviderServiceSkillRepository skills;
 private final ProviderProfessionalCertificateRepository certificates;
 private final ProviderEducationQualificationRepository education;
 private final ServiceCustomFieldRepository fields;
 private final ProviderCustomFieldSubmissionRepository submissions;
 private final ProviderServiceOptionRepository providerOptions;
 private final ServiceOptionRepository options;
 private final ProviderServiceApprovalService approvals;
 private final ProviderDocumentService documentReviews;
 private final ProviderQualificationService qualificationReviews;
 private final ServiceCustomFieldService customReviews;
 private final ServiceOptionService optionReviews;
 private final AuditService audit;
 private final ObjectMapper mapper;
 private final EntityManager entityManager;

 public Page<Summary> list(String status,Pageable pageable) {
  if (!Set.of("PENDING","APPROVED","REJECTED","ALL").contains(status)) throw new IllegalArgumentException("Status must be PENDING, APPROVED, REJECTED or ALL");
  return providers.findApprovalProviders(status,pageable).map(p -> {
   Detail d=build(p);
   return new Summary(d.providerId(),d.providerName(),d.pendingCount(),d.missingCount(),d.services().stream()
       .map(s -> new ServiceSummary(s.assignmentId(),s.serviceName(),s.verificationStatus(),s.pendingCount(),s.missingCount())).toList());
  });
 }
 public Detail detail(Long providerId) {
  return build(providers.findById(providerId).orElseThrow(() -> new ResourceNotFoundException("Provider not found")));
 }
 private Detail build(ServiceProvider provider) {
  List<ServiceDetail> services=assignments.findAllByServiceProviderId(provider.getServiceProviderId()).stream()
      .sorted(Comparator.comparing(ServiceProviderTypeAssignment::getAssignmentId)).map(this::service).toList();
  int pending=(int)services.stream().flatMap(s -> s.items().stream()).filter(Requirement::reviewable)
      .map(r -> r.type()+":"+r.id()).distinct().count();
  String name=Stream.of(provider.getFirstName(),provider.getLastName()).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
  return new Detail(provider.getServiceProviderId(),name,pending,services.stream().mapToInt(ServiceDetail::missingCount).sum(),services);
 }
 private ServiceDetail service(ServiceProviderTypeAssignment a) {
  ServiceProviderType type=types.findById(a.getServiceProviderTypeId()).orElseThrow(() -> new ResourceNotFoundException("Service type not found"));
  List<ProviderServiceOption> selections=providerOptions.findAllByAssignmentId(a.getAssignmentId());
  Set<Long> selected=selections.stream().map(ProviderServiceOption::getOptionId).collect(Collectors.toSet());
  List<Requirement> docs=new ArrayList<>(),sk=new ArrayList<>(),cert=new ArrayList<>(),edu=new ArrayList<>(),dyn=new ArrayList<>(),opts=new ArrayList<>();
  var uploads=documents.findAllByServiceProviderIdOrderByCreatedAtDesc(a.getServiceProviderId());
  for(var def:documentRequirements.findAllByServiceProviderTypeId(a.getServiceProviderTypeId())) {
   if(def.getOptionId()!=null && !selected.contains(def.getOptionId())) continue;
   var item=uploads.stream().filter(x -> Objects.equals(x.getDocumentTypeId(),def.getDocumentTypeId())).findFirst().orElse(null);
   String label=documentTypes.findById(def.getDocumentTypeId()).map(DocumentType::getName).orElse("Document "+def.getDocumentTypeId());
   docs.add(row("DOCUMENT",item==null?null:item.getDocumentId(),def.getRequirementId(),label,def.isMandatory(),def.isRequiresApproval(),
       item==null?null:item.getVerificationStatus(),item==null?null:item.getDocumentUrl(),item==null?null:item.getRejectionReason(),
       item==null?null:item.getExpiryDate(),null,info("documentTypeId",def.getDocumentTypeId(),"optionId",def.getOptionId(),"documentNumber",item==null?null:item.getDocumentNumber())));
  }
  var selectedSkills=skills.findAllByAssignmentId(a.getAssignmentId());
  for(var def:skillDefinitions.findAllByServiceProviderTypeIdOrderByNameAsc(a.getServiceProviderTypeId())) {
   var item=selectedSkills.stream().filter(x -> Objects.equals(x.getSkillId(),def.getSkillId())).findFirst().orElse(null);
   sk.add(row("SKILL",item==null?null:item.getProviderSkillId(),def.getSkillId(),def.getName(),def.isMandatory(),def.isRequiresApproval(),
       item==null?null:item.getVerificationStatus(),null,item==null?null:item.getRejectionReason(),null,a.getAssignmentId(),null));
  }
  boolean certRequired="REQUIRED".equals(type.getCertificateRequirement());
  var certItems=certificates.findAllByAssignmentIdOrderByCreatedAtDesc(a.getAssignmentId());
  for(var item:certItems) cert.add(row("CERTIFICATE",item.getCertificateId(),null,item.getName(),certRequired,type.isCertificateRequiresApproval(),item.getVerificationStatus(),item.getDocumentKey(),item.getRejectionReason(),item.getExpiryDate(),a.getAssignmentId(),info("issuingOrganization",item.getIssuingOrganization(),"certificateNumber",item.getCertificateNumber(),"issuedDate",item.getIssuedDate())));
  if(certRequired && certItems.isEmpty())cert.add(row("CERTIFICATE",null,null,"Professional certificate",true,type.isCertificateRequiresApproval(),null,null,null,null,a.getAssignmentId(),null));
  boolean eduRequired="REQUIRED".equals(type.getEducationRequirement());
  var eduItems=education.findAllByAssignmentIdOrderByCreatedAtDesc(a.getAssignmentId());
  for(var item:eduItems)edu.add(row("EDUCATION",item.getEducationId(),null,item.getQualificationName(),eduRequired,type.isEducationRequiresApproval(),item.getVerificationStatus(),item.getDocumentKey(),item.getRejectionReason(),null,a.getAssignmentId(),info("instituteName",item.getInstituteName(),"fieldOfStudy",item.getFieldOfStudy(),"startDate",item.getStartDate(),"completionDate",item.getCompletionDate(),"result",item.getResult())));
  if(eduRequired && eduItems.isEmpty())edu.add(row("EDUCATION",null,null,"Education qualification",true,type.isEducationRequiresApproval(),null,null,null,null,a.getAssignmentId(),null));
  var definitions=fields.findAllByServiceProviderTypeIdAndScopeAndStatusOrderByDisplayOrderAscLabelAsc(a.getServiceProviderTypeId(),"PROVIDER_REQUIREMENT","ACTIVE");
  Map<Long,ProviderCustomFieldSubmission> byField=new HashMap<>();submissions.findAllByAssignmentId(a.getAssignmentId()).forEach(s -> byField.put(s.getFieldId(),s));
  Map<String,ServiceCustomField> byCode=new HashMap<>();definitions.forEach(f -> byCode.put(f.getCode(),f));
  for(var def:definitions) {
   if(!def.appliesToOptions(selected) || !approvals.conditionApplies(a,def,byCode,byField)) continue;
   var item=byField.get(def.getFieldId());
   if(item==null)item=approvals.reusableSubmission(a,def).orElse(null);
   Long source=item==null?a.getAssignmentId():item.getAssignmentId();
   boolean reused=item!=null && !Objects.equals(source,a.getAssignmentId());
   JsonNode value=item==null?null:parse(item.getValueJson());
   var r=row("DYNAMIC_REQUIREMENT",item==null?null:item.getSubmissionId(),def.getFieldId(),def.getLabel(),def.isRequired(),def.isRequiresApproval(),item==null?null:item.getVerificationStatus(),null,item==null?null:item.getReviewNote(),null,source,
       info("code",def.getCode(),"fieldType",def.getFieldType(),"reuseScope",def.getReuseScope(),"reused",reused,"value",value,"optionIds",def.effectiveOptionIds()));
   dyn.add(r);
  }
  for(var item:selections) {
   var def=options.findById(item.getOptionId()).orElse(null);
   opts.add(row("SERVICE_OPTION",item.getProviderOptionId(),item.getOptionId(),def==null?"Unknown option":def.getName(),type.isOptionsRequired(),true,item.getVerificationStatus(),null,item.getReviewNote(),null,a.getAssignmentId(),info("providerStatus",item.getProviderStatus(),"adminStatus",item.getAdminStatus(),"configurationStatus",def==null?null:def.getStatus())));
  }
  if(type.isOptionsRequired() && selections.isEmpty())opts.add(row("SERVICE_OPTION",null,null,"Select service options",true,true,null,null,null,null,a.getAssignmentId(),info("minimumSelections",type.getMinimumOptionSelections()==null?1:type.getMinimumOptionSelections())));
  Map<String,Boolean> checks=new LinkedHashMap<>();
  checks.put("documents",approvals.documentsComplete(a));checks.put("skills",approvals.skillsComplete(a));
  checks.put("certificates",approvals.certificatesComplete(a));checks.put("education",approvals.educationComplete(a));
  checks.put("availability",approvals.availabilityComplete(a));checks.put("serviceAreas",approvals.serviceAreasComplete(a));
  checks.put("serviceOptions",approvals.optionsComplete(a));checks.put("dynamicRequirements",approvals.customRequirementsComplete(a));
  var all=Stream.of(docs,sk,cert,edu,dyn,opts).flatMap(List::stream).toList();
  return new ServiceDetail(a.getAssignmentId(),a.getServiceProviderTypeId(),type.getName(),a.getVerificationStatus(),a.getProviderStatus(),a.getAdminStatus(),a.isEmergencyOverride(),checks,
      (int)all.stream().filter(Requirement::reviewable).map(r -> r.type()+":"+r.id()).distinct().count(),(int)all.stream().filter(r -> r.required()&&r.missing()).count(),docs,sk,cert,edu,dyn,opts);
 }
 private Requirement row(String type,Long id,Long definition,String name,boolean required,boolean approval,String status,String key,String reason,LocalDate expiry,Long source,JsonNode details) {
  boolean expired=expiry!=null && expiry.isBefore(LocalDate.now());
  return new Requirement(type,id,definition,name,required,approval,id==null?"MISSING":status,id==null,expired,id!=null&&approval&&!expired&&"PENDING".equals(status),key,reason,expiry,source,details);
 }
 private ObjectNode info(Object... pairs) {ObjectNode n=mapper.createObjectNode();for(int i=0;i<pairs.length;i+=2)n.set((String)pairs[i],mapper.valueToTree(pairs[i+1]));return n;}
 private JsonNode parse(String value) {try{return mapper.readTree(value);}catch(Exception e){return mapper.getNodeFactory().textNode(value==null?"":value);}}

 @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
 public BulkResponse bulk(Long actorId,BulkRequest request) {
  if(request.reviews()==null || request.reviews().isEmpty() || request.reviews().size()>100)throw new IllegalArgumentException("Review batch must contain 1 to 100 items");
  var a=assignments.findById(request.assignmentId()).orElseThrow(() -> new ResourceNotFoundException("Provider service assignment not found"));
  providers.lockById(a.getServiceProviderId()).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
  Set<String> seen=new HashSet<>();
  for(var r:request.reviews()) {
   if(r==null || r.type()==null || r.status()==null || r.id()==null || r.id()<=0 || !Set.of("APPROVED","REJECTED").contains(r.status()))throw new IllegalArgumentException("Invalid review");
   if(!seen.add(r.type()+":"+r.id()))throw new IllegalArgumentException("Duplicate review item");
   if("REJECTED".equals(r.status()) && (r.rejectionReason()==null || r.rejectionReason().isBlank()))throw new IllegalArgumentException("Rejection reason is required");
   if(r.rejectionReason()!=null && r.rejectionReason().length()>500)throw new IllegalArgumentException("Rejection reason exceeds 500 characters");
  }
  // Stable row-lock order, then read current applicability/state before any writes.
  request.reviews().stream().sorted(Comparator.comparing(Review::type).thenComparing(Review::id)).forEach(r -> {
   Class<?> entity=switch(r.type()) {
    case "DOCUMENT" -> ServiceProviderDocument.class;case "SKILL" -> ProviderServiceSkill.class;
    case "CERTIFICATE" -> ProviderProfessionalCertificate.class;case "EDUCATION" -> ProviderEducationQualification.class;
    case "DYNAMIC_REQUIREMENT" -> ProviderCustomFieldSubmission.class;case "SERVICE_OPTION" -> ProviderServiceOption.class;
    default -> throw new IllegalArgumentException("Unsupported review type");
   };
   if(entityManager.find(entity,r.id(),LockModeType.PESSIMISTIC_WRITE)==null)throw new ResourceNotFoundException("Review item not found");
  });
  ServiceDetail before=service(a);
  for(var r:request.reviews()) {
   var target=before.items().stream().filter(i -> i.type().equals(r.type())&&Objects.equals(i.id(),r.id())).findFirst()
       .orElseThrow(() -> new ResourceNotFoundException("Item does not belong to this provider service or is not applicable"));
   if(!target.reviewable() || (target.sourceAssignmentId()!=null && !target.sourceAssignmentId().equals(a.getAssignmentId())))throw new ConflictException("Item is not pending or eligible for this assignment");
  }
  for(var r:request.reviews()) {
   var review=new DocumentReviewRequest(r.status(),r.rejectionReason());
   switch(r.type()) {
    case "DOCUMENT" -> documentReviews.review(r.id(),actorId,review);
    case "SKILL" -> qualificationReviews.reviewSkill(r.id(),actorId,review);
    case "CERTIFICATE" -> qualificationReviews.reviewCertificate(r.id(),actorId,review);
    case "EDUCATION" -> qualificationReviews.reviewEducation(r.id(),actorId,review);
    case "DYNAMIC_REQUIREMENT" -> customReviews.review(actorId,r.id(),review);
    case "SERVICE_OPTION" -> optionReviews.review(actorId,r.id(),review);
    default -> throw new IllegalArgumentException("Unsupported review type");
   }
  }
  approvals.refreshProviderApprovals(a.getServiceProviderId());
  audit.record(actorId,"PROVIDER_APPROVAL_BULK_REVIEW","PROVIDER_SERVICE_ASSIGNMENT",a.getAssignmentId(),null,request);
  return new BulkResponse(true,request.reviews().size(),detail(a.getServiceProviderId()));
 }
}
