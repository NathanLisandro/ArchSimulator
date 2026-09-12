package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.DeckView;
import dev.archsim.application.dto.ReviewCardView;
import dev.archsim.application.usecase.StudyDecks;
import dev.archsim.domain.flashcard.ReviewGrade;
import dev.archsim.infrastructure.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/study")
public class StudyController {
    public record ReviewRequest(String grade) {}

    private final StudyDecks studyDecks;

    public StudyController(StudyDecks studyDecks) { this.studyDecks = studyDecks; }

    @GetMapping("/decks")
    public List<DeckView> decks() { return studyDecks.decks(CurrentUser.id()); }

    @GetMapping("/decks/{deckId}/next")
    public ResponseEntity<ReviewCardView> next(@PathVariable String deckId) {
        return studyDecks.next(CurrentUser.id(), deckId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/cards/{cardId}/review")
    public ReviewCardView.Result review(@PathVariable String cardId, @RequestBody ReviewRequest req) {
        return studyDecks.review(CurrentUser.id(), cardId, ReviewGrade.valueOf(req.grade().toUpperCase()));
    }
}
