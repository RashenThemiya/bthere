package com.jobhub.entity.customer;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "favorite_service_provider")
@Getter
@Setter
@NoArgsConstructor
public class FavoriteServiceProvider extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long favoriteId;
    private Long customerId;
    private Long serviceProviderId;
}

