package com.community.servercore.duel;

public final class RatingService {
    private final int kFactor;

    public RatingService() {
        this(32);
    }

    public RatingService(int kFactor) {
        if (kFactor < 1 || kFactor > 128) {
            throw new IllegalArgumentException("kFactor must be between 1 and 128");
        }
        this.kFactor = kFactor;
    }

    public RatingChange calculate(int winnerRating, int loserRating) {
        if (winnerRating < 0 || loserRating < 0) {
            throw new IllegalArgumentException("ratings must not be negative");
        }
        double winnerExpected = expectedScore(winnerRating, loserRating);
        double loserExpected = expectedScore(loserRating, winnerRating);
        int updatedWinner = Math.max(0, (int) Math.round(winnerRating + kFactor * (1.0 - winnerExpected)));
        int updatedLoser = Math.max(0, (int) Math.round(loserRating + kFactor * (0.0 - loserExpected)));
        return new RatingChange(updatedWinner, updatedLoser);
    }

    private static double expectedScore(int rating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - rating) / 400.0));
    }

    public record RatingChange(int winnerRating, int loserRating) {
        public RatingChange {
            if (winnerRating < 0 || loserRating < 0) {
                throw new IllegalArgumentException("ratings must not be negative");
            }
        }
    }
}
