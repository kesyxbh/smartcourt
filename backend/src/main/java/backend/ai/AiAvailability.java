package backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Answers one question: is a real LLM API key configured?
 *
 * <p>The application must start without a key (the Spring AI starter fails on startup if the key
 * property is empty, so application.properties supplies the placeholder "not-configured"). The AI
 * endpoints therefore check this at request time and answer 503 instead.
 */
@Component
public class AiAvailability {

    static final String PLACEHOLDER_KEY = "not-configured";

    private final String apiKey;

    public AiAvailability(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !PLACEHOLDER_KEY.equals(apiKey);
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI is not configured. Set the LLM_API_KEY environment variable and restart the backend.");
        }
    }
}
