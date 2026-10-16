package backend.controller;

import backend.ai.AiChatService;
import backend.ai.AiSearchService;
import backend.dto.AiChatRequest;
import backend.dto.AiChatResponse;
import backend.dto.AiSearchRequest;
import backend.dto.AiSearchResponse;
import backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Both endpoints require a signed-in user (SecurityConfig: anything not explicitly public is
 * protected), which also keeps anonymous visitors from spending LLM tokens.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiChatService chatService;
    private final AiSearchService searchService;

    public AiController(AiChatService chatService, AiSearchService searchService) {
        this.chatService = chatService;
        this.searchService = searchService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@AuthenticationPrincipal UserPrincipal principal,
                               @Valid @RequestBody AiChatRequest request) {
        return chatService.chat(principal.id(), request);
    }

    @PostMapping("/search")
    public AiSearchResponse search(@Valid @RequestBody AiSearchRequest request) {
        return searchService.search(request);
    }
}
