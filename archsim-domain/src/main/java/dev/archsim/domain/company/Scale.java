package dev.archsim.domain.company;

public record Scale(long dailyActiveUsers, int peakTps, String dataVolume, String growthNote) {}
