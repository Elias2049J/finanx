package com.elias.mismonedas.document.controller;

import com.elias.mismonedas.document.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
@Tag(name = "Images", description = "Almacenamiento generico de imagenes")
public class ImageController {

    private final ImageService imageService;

    @Operation(
            summary = "Descargar imagen",
            description = "Redirige a una URL prefirmada de corta duración después de validar que la imagen pertenece al usuario autenticado.",
            operationId = "downloadImage",
            tags = {"Images"},
            responses = {
                    @ApiResponse(responseCode = "302", description = "Redirección a SeaweedFS"),
                    @ApiResponse(responseCode = "404", description = "Imagen no encontrada"),
                    @ApiResponse(responseCode = "401", description = "Usuario no autenticado")
            }
    )
    @GetMapping("/{imageId}")
    public ResponseEntity<Void> download(
            @Parameter(description = "ID de la imagen") @PathVariable Long imageId) {
        return imageService.getPresignedDownloadUrl(imageId)
                .map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .cacheControl(CacheControl.noStore())
                        .location(url)
                        .<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
