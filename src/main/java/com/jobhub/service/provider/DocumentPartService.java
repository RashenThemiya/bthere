package com.jobhub.service.provider;

import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.DocumentType;
import com.jobhub.service.upload.S3PrivateDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentPartService {
    private static final Set<String> MIME_TYPES=Set.of("image/jpeg","image/png","image/webp","application/pdf");
    private final S3PrivateDocumentService storage;

    public void validateDefinitions(List<DocumentPartDefinition> parts) {
        var codes=new HashSet<String>();
        for(var part:parts) {
            if(part==null || part.code()==null || !part.code().matches("[A-Z][A-Z0-9_]{0,49}")
                    || !codes.add(part.code()) || part.label()==null || part.label().isBlank()
                    || part.label().length()>150 || part.maximumFiles()<1 || part.maximumFiles()>100
                    || part.maximumFileSizeMb()<1 || part.maximumFileSizeMb()>100
                    || part.allowedFileTypes()==null || part.allowedFileTypes().isEmpty()
                    || part.allowedFileTypes().stream().anyMatch(t->t==null || !MIME_TYPES.contains(t)))
                throw new IllegalArgumentException("Invalid document part definition or duplicate code");
        }
    }

    /** Complete replacement: no pending partial bundle can satisfy provider approval. */
    public List<DocumentPartFiles> validateSubmission(Long userId, DocumentType type, ProviderDocumentRequest request) {
        var definitions=type.effectiveParts();
        if(definitions.isEmpty()) {
            if(request.parts()!=null && !request.parts().isEmpty())
                throw new IllegalArgumentException("This document type uses a single documentKey");
            validateOwnedKey(userId,request.documentKey());
            return List.of();
        }
        if(request.documentKey()!=null)
            throw new IllegalArgumentException("Use parts instead of documentKey for this document type");
        var files=request.parts()==null ? List.<DocumentPartFiles>of() : request.parts();
        var byCode=new HashMap<String,DocumentPartDefinition>();
        definitions.forEach(d->byCode.put(d.code(),d));
        var seen=new HashSet<String>(); var keys=new HashSet<String>();
        for(var part:files) {
            var definition=part==null ? null : byCode.get(part.code());
            if(definition==null || !seen.add(part.code()))
                throw new IllegalArgumentException("Unknown or duplicate document part");
            if(part.documentKeys()==null || part.documentKeys().isEmpty()
                    || part.documentKeys().size()>definition.maximumFiles())
                throw new IllegalArgumentException("Invalid file count for part "+part.code());
            for(String key:part.documentKeys()) {
                validateOwnedKey(userId,key);
                if(!keys.add(key)) throw new IllegalArgumentException("A file cannot be used for multiple document parts");
                var file=storage.inspectProviderFile(userId,key);
                if(!definition.allowedFileTypes().contains(file.contentType()) || file.size()<=0
                        || file.size()>definition.maximumFileSizeMb()*1024L*1024L)
                    throw new IllegalArgumentException("Invalid file type or size for part "+part.code());
            }
        }
        if(files.isEmpty() || definitions.stream().anyMatch(d->d.required() && !seen.contains(d.code())))
            throw new IllegalArgumentException("All required document parts must be supplied");
        return List.copyOf(files);
    }

    private void validateOwnedKey(Long userId,String key) {
        if(key==null || key.length()>2048 || key.contains("..")
                || !key.matches("providers/"+userId+"/documents/[A-Za-z0-9/_-]+\\.[A-Za-z0-9]+"))
            throw new IllegalArgumentException("Document key does not belong to this provider");
    }
}
