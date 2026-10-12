package com.jobhub.entity.provider;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ServiceMediaValue {
    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;
    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;
    @Column(name = "url", nullable = false, length = 2048)
    private String url;
    @Column(name = "alt_text", length = 300)
    private String altText;
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
