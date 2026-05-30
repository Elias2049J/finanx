package com.elias.finanx.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class FileExportDTO {
    private String filename;
    private String mimetype;
    private String date;
    private byte[] data;
}
