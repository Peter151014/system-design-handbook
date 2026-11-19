
package com.example.cloudfile.controller;

import com.example.cloudfile.dto.FileResponse;
import com.example.cloudfile.service.FileService;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(
            value = "/uploadFile",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public FileResponse uploadFile(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("email") String email,
            @RequestParam("file") MultipartFile file
    ) {
        return fileService.uploadFile(
                idempotencyKey,
                email,
                file
        );
    }
}
