package backend.dto;

public record LoginResponse(String token, UserResponse user) {
}
