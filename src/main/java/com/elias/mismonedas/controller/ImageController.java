package com.elias.mismonedas.controller;

import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Almacenamiento generico de imagenes")
public class ImageController {

    private final ImageService imageService;

    @Operation(
            summary = "Descargar imagen",
            description = "Retorna los bytes de una imagen por su ID.",
            operationId = "downloadImage",
            tags = {"Images"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Imagen encontrada"),
                    @ApiResponse(responseCode = "404", description = "Imagen no encontrada")
            }
    )
    @GetMapping("/{imageId}")
    public ResponseEntity<byte[]> download(
            @Parameter(description = "ID de la imagen") @PathVariable Long imageId) {

        Image image = imageService.get(imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.getFilename() + "\"")
                .contentLength(image.getData().length)
                .body(image.getData());
    }
}
