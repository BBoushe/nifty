package de.fhdortmund.nifty.assessment.service;

import de.fhdortmund.nifty.shared.exception.FieldErrorDetail;
import de.fhdortmund.nifty.shared.exception.FieldValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * REQ-AC-15 / Decision 4.3: the single place that scores BFI-10. Other slices read the stored trait means and never
 * reverse an item again. Pure and Stream-free (coding standard #3).
 */
final class Bfi10ScoringService {

    static final int ITEM_COUNT = 10;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private Bfi10ScoringService() {
    }

    /** REQ-AC-14: every item needs an answer 1..5. Names each bad item as {@code items[i]} (0-based). */
    static int[] validate(List<Integer> items) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        int[] answers = new int[ITEM_COUNT];
        for (int i = 0; i < ITEM_COUNT; i++) {
            Integer value = items != null && i < items.size() ? items.get(i) : null;
            if (value == null || value < 1 || value > 5) {
                errors.add(new FieldErrorDetail("items[" + i + "]",
                        "Item " + (i + 1) + " needs an answer from 1 to 5.", "BFI10_ITEM"));
            } else {
                answers[i] = value;
            }
        }
        if (items != null && items.size() > ITEM_COUNT) {
            errors.add(new FieldErrorDetail("items", "BFI-10 has exactly 10 items.", "BFI10_ITEM"));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return answers;
    }

    /** Authors' key: Extraversion 1R, 6; Agreeableness 2, 7R; Conscientiousness 3R, 8; Neuroticism 4R, 9; Openness 5R, 10. */
    static Bfi10Traits score(int[] items) {
        return new Bfi10Traits(
                mean(reversed(items[2]), items[7]),
                mean(items[1], reversed(items[6])),
                mean(reversed(items[3]), items[8]),
                mean(reversed(items[0]), items[5]),
                mean(reversed(items[4]), items[9]));
    }

    private static int reversed(int answer) {
        return 6 - answer;
    }

    private static BigDecimal mean(int a, int b) {
        return BigDecimal.valueOf(a + b).divide(TWO);
    }
}
