package com.example.debate_v2;

public class LeaderboardModel {

    private String username;
    private double score;

    public LeaderboardModel(String username, double score) {
        this.username = username;
        this.score = score;
    }

    public String getUsername() {
        return username;
    }

    public double getScore() {
        return score;
    }
}
