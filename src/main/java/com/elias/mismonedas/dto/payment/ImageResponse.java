package com.elias.mismonedas.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Metadatos de una imagen almacenada")
public class ImageResponse {

    @Schema(description = "ID de la imagen", example = "1")
    private Long imageId;

    @Schema(description = "Nombre original del archivo", example = "recibo.jpg")
    private String filename;

    @Schema(description = "Tipo MIME del contenido", example = "image/jpeg")
    private String mediaType;

    @Schema(description = "Tamaño en bytes", example = "204800")
    private Long sizeBytes;
}
