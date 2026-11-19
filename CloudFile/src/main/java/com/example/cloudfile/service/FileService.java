
package com.example.cloudfile.service;

import com.example.cloudfile.dto.FileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileResponse uploadFile(
            String idempotencyKey,
            String email,
            MultipartFile file
    );
}
