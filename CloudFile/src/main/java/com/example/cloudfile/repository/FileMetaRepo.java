package com.example.cloudfile.repository;

import com.example.cloudfile.entity.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileMetaRepo extends JpaRepository<FileMetaData,Long> {

}
