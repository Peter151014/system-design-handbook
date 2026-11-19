package com.example.cloudfile.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetaData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String objectKey;

    private String ContentType;

    private Long FileSize;

    private LocalDateTime uploadedAt;

    @PrePersist
    public void createdAt(){
        this.uploadedAt = LocalDateTime.now();
    }

}
