package com.elias.mismonedas.document.controller;

import com.elias.mismonedas.document.service.ImageService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageControllerTest {

    private final ImageService imageService = mock(ImageService.class);
    private final ImageController controller = new ImageController(imageService);

    @Test
    void shouldRedirectToPresignedImageUrl() {
        URI signedUrl = URI.create("http://localhost:8333/finanx-images/images/1/a.jpg?signature=short-lived");
        when(imageService.getPresignedDownloadUrl(14L)).thenReturn(Optional.of(signedUrl));

        var response = controller.download(14L);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isEqualTo(signedUrl);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void shouldReturnNotFoundWhenImageIsNotOwnedByAuthenticatedUser() {
        when(imageService.getPresignedDownloadUrl(14L)).thenReturn(Optional.empty());

        var response = controller.download(14L);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getLocation()).isNull();
    }
}
