package backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSearchRequest(@NotBlank @Size(max = 500) String query) {
}
