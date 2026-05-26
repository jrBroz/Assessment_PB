package br.edu.infnet.product.domain.model;

import br.edu.infnet.product.domain.enums.Platform;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payments")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private BigDecimal price;
    @Column(nullable = false)
    private Platform platform;
    @Column(nullable = false)
    private Integer stockQuantity;
    @Column(nullable = false)
    private LocalDateTime releaseDate;
}
