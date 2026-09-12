package dev.archsim.application.dto;

import dev.archsim.domain.observability.Trace;

import java.util.List;

public record TracesView(List<Trace> traces, int total) {}
