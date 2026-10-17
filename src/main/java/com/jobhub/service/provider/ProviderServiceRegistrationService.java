package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProviderServiceRegistrationService {
    private final ProviderTypeService typeService;
    private final ServiceOptionService optionService;
    private final ProviderDocumentService documentService;
    private final ProviderQualificationService qualificationService;
    private final ProviderAvailabilityService availabilityService;
    private final ServiceCustomFieldService fieldService;
    private final ProviderOnboardingService onboardingService;

    @Transactional
    public ProviderServiceRegistrationResponse register(Long userId, ProviderServiceRegistrationRequest request) {
        if (typeService.getAssignments(userId).stream().anyMatch(
                item -> item.providerTypeId().equals(request.service().providerTypeId()))) {
            throw new ConflictException("Service is already registered; use the individual update APIs");
        }
        Set<String> groups = new HashSet<>();
        for (var group : request.serviceAreas()) {
            if (!request.options().optionIds().contains(group.optionId())) {
                throw new IllegalArgumentException("Service area option must be selected in this registration");
            }
            if (!groups.add(group.optionId() + ":" + group.deliveryMode().trim().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate option and delivery mode service area group");
            }
        }
        Long assignmentId = typeService.addAssignment(userId, request.service()).assignmentId();
        optionService.replaceProviderOptions(userId, assignmentId, request.options());
        optionService.replaceLanguages(userId, assignmentId, request.languages());
        for (var document : request.documents()) documentService.addDocument(userId, document);
        qualificationService.replaceSkills(userId, assignmentId, request.skills());
        for (var certificate : request.certificates()) {
            qualificationService.addCertificate(userId, assignmentId, certificate);
        }
        for (var education : request.education()) {
            qualificationService.addEducation(userId, assignmentId, education);
        }
        if (request.availability() != null) {
            availabilityService.replaceSchedule(userId, assignmentId, request.availability());
        }
        for (var date : request.unavailableDates()) {
            availabilityService.addUnavailableDate(userId, assignmentId, date);
        }
        for (var group : request.serviceAreas()) {
            availabilityService.replaceServiceAreas(userId, assignmentId, group.optionId(),
                    group.deliveryMode(), group.locations());
        }
        fieldService.saveSubmissions(userId, assignmentId, request.requirements());
        var assignment = typeService.getAssignments(userId).stream()
                .filter(item -> item.assignmentId().equals(assignmentId)).findFirst().orElseThrow();
        return new ProviderServiceRegistrationResponse(assignment, onboardingService.get(userId));
    }
}
