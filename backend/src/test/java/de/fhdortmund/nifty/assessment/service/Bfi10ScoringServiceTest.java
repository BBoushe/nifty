package de.fhdortmund.nifty.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** REQ-AC-14 / REQ-AC-15 / Decision 4.3: single-source BFI-10 key (1R, 3R, 4R, 5R, 7R reverse as 6 - x). */
class Bfi10ScoringServiceTest {

    @Test
    void allOnesAndAllFivesGiveThreeForEveryTraitBecauseEachTraitPairsOneReversedItem() {
        Bfi10Traits ones = Bfi10ScoringService.score(new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
        Bfi10Traits fives = Bfi10ScoringService.score(new int[] {5, 5, 5, 5, 5, 5, 5, 5, 5, 5});

        for (Bfi10Traits traits : List.of(ones, fives)) {
            assertThat(traits.extraversion()).isEqualByComparingTo("3.0");
            assertThat(traits.agreeableness()).isEqualByComparingTo("3.0");
            assertThat(traits.conscientiousness()).isEqualByComparingTo("3.0");
            assertThat(traits.neuroticism()).isEqualByComparingTo("3.0");
            assertThat(traits.openness()).isEqualByComparingTo("3.0");
        }
    }

    @Test
    void mixedAnswersApplyTheAuthorsKey() {
        Bfi10Traits traits = Bfi10ScoringService.score(new int[] {2, 4, 1, 5, 3, 4, 2, 5, 1, 4});

        assertThat(traits.extraversion()).isEqualByComparingTo("4.0");      // (6-2 + 4) / 2
        assertThat(traits.agreeableness()).isEqualByComparingTo("4.0");     // (4 + 6-2) / 2
        assertThat(traits.conscientiousness()).isEqualByComparingTo("5.0"); // (6-1 + 5) / 2
        assertThat(traits.neuroticism()).isEqualByComparingTo("1.0");       // (6-5 + 1) / 2
        assertThat(traits.openness()).isEqualByComparingTo("3.5");          // (6-3 + 4) / 2
        assertThat(traits.asMap()).containsOnlyKeys(
                "conscientiousness", "agreeableness", "neuroticism", "extraversion", "openness");
        assertThat(traits.asMap()).containsEntry("openness", 3.5);
    }

    @Test
    void validateReturnsTheTenAnswersInOrder() {
        assertThat(Bfi10ScoringService.validate(List.of(1, 2, 3, 4, 5, 5, 4, 3, 2, 1)))
                .containsExactly(1, 2, 3, 4, 5, 5, 4, 3, 2, 1);
    }

    @Test
    void validateNamesEveryMissingOrOutOfRangeItem() {
        List<Integer> items = Arrays.asList(3, 3, 3, 3, 3, 3, null, 3, 6);

        assertThatThrownBy(() -> Bfi10ScoringService.validate(items))
                .isInstanceOfSatisfying(FieldValidationException.class, ex -> assertThat(ex.getErrors())
                        .extracting(FieldErrorDetail::field)
                        .containsExactly("items[6]", "items[8]", "items[9]"));
        // REQ-AC-14: the message names the item as the candidate sees it (1-based).
        assertThatThrownBy(() -> Bfi10ScoringService.validate(items))
                .isInstanceOfSatisfying(FieldValidationException.class, ex -> assertThat(ex.getErrors().getFirst()
                        .message()).isEqualTo("Item 7 needs an answer from 1 to 5."));
        assertThatThrownBy(() -> Bfi10ScoringService.validate(null)).isInstanceOf(FieldValidationException.class);
    }
}
